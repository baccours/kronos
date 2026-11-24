package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The announcer's state machine under every interleaving of the main thread, the worker thread,
 * audio focus and the audio hardware. See AudioFakes for how time and threads are controlled.
 */
class ClipAnnouncerTest {

    private val main = FakeMainThread()
    private val worker = FakeWorker()
    private val library = FakeClipSource()
    private val focus = FakeFocusGate()
    private val clips = FakeClipFactory()
    private val announcer = ClipAnnouncer(focus, library, main, worker, clips)

    private val go = Cue.Speak(Speech.Go)
    private val rest = Cue.Speak(Speech.Rest)
    private val getReady = Cue.Speak(Speech.GetReady)
    private val lead = ClipAnnouncer.LEAD_SILENCE.size

    /** Worker finishes its queued work, then the main thread delivers the results. */
    private fun prepareAndDeliver() { worker.runAll(); main.runDue() }

    // ---- The normal life of one announcement --------------------------------------------------

    @Test
    fun anAnnouncementIsPreparedOnTheWorkerAndPlayedOnTheMainThread() {
        assertTrue(announcer.say(go))
        assertTrue(announcer.isSpeaking)
        assertEquals(1, focus.requests)
        assertTrue("nothing is built on the caller's thread", clips.created.isEmpty())

        worker.runAll()
        assertEquals(1, clips.created.size)
        assertFalse("playback starts on the main thread only", clips.created.single().started)

        main.runDue()
        assertTrue(clips.created.single().started)
        assertTrue(announcer.isSpeaking)
    }

    @Test
    fun theClipIsReleasedAndFocusScheduledForReleaseOnceTheLastFrameIsPlayed() {
        announcer.say(go)
        prepareAndDeliver()
        val clip = clips.created.single()

        clip.reachLastFrame()
        main.advanceBy(ClipAnnouncer.OUTPUT_TAIL_MS - 1)
        assertTrue("the output tail lets the last frames flush", announcer.isSpeaking)

        main.advanceBy(1)
        assertTrue(clip.released)
        assertFalse(announcer.isSpeaking)
        assertEquals(listOf(AudioFocusController.TAIL_MS), focus.releaseAfterCalls)
        assertEquals("no stray timers left behind", 0, main.pending)
    }

    @Test
    fun ifTheHardwareNeverReportsTheEndTheFallbackTimeoutEndsPlayback() {
        announcer.say(go)
        prepareAndDeliver()
        val clip = clips.created.single()

        main.advanceBy(ClipAnnouncer.FALLBACK_MS - 1)
        assertTrue(announcer.isSpeaking)
        main.advanceBy(1)
        assertTrue(clip.released)
        assertFalse(announcer.isSpeaking)
    }

    @Test
    fun theEndNotificationReplacesTheFallbackSoNothingFinishesTwice() {
        announcer.say(go)
        prepareAndDeliver()
        val clip = clips.created.single()
        main.advanceBy(100)
        clip.reachLastFrame()

        main.advanceBy(ClipAnnouncer.OUTPUT_TAIL_MS)
        assertFalse(announcer.isSpeaking)
        assertEquals(0, main.pending) // the fallback was canceled
        main.advanceBy(10_000) // nothing else fires
        assertEquals(listOf(AudioFocusController.TAIL_MS), focus.releaseAfterCalls)
    }

    @Test
    fun interruptingAnnouncementsStartWithTheLeadSilenceAndPlainOnesDoNot() {
        library.missing.clear()
        val names = ClipCatalog.clipsFor(go).size

        announcer.say(go)
        worker.runAll()
        assertEquals(3 * names, clips.created.last().frames)

        announcer.say(Cue.Speak(Speech.Go, interrupt = true))
        worker.runAll()
        assertEquals(lead + 3 * names, clips.created.last().frames)
    }

    // ---- Queueing and interrupting ----------------------------------------------------------

    @Test
    fun queuedAnnouncementsPlayInOrderOneAtATime() {
        announcer.say(go)
        announcer.say(rest)
        prepareAndDeliver()
        val (first, second) = clips.created

        assertTrue(first.started)
        assertFalse("the second waits for the first", second.started)

        first.reachLastFrame()
        main.advanceBy(ClipAnnouncer.OUTPUT_TAIL_MS)
        assertTrue(first.released)
        assertTrue(second.started)
    }

    @Test
    fun anInterruptingCueCutsWhatIsPlayingAndDiscardsWhatIsQueued() {
        announcer.say(go)
        announcer.say(rest)
        prepareAndDeliver()
        val (playing, queued) = clips.created

        announcer.say(Cue.Speak(Speech.GetReady, interrupt = true))

        assertTrue(playing.released)
        assertTrue(queued.released)
        assertTrue(announcer.isSpeaking) // the new one is on its way

        prepareAndDeliver()
        assertTrue(clips.created.last().started)
        assertEquals(3, clips.created.size)
    }

    @Test
    fun theCutAnnouncementsTimersCannotEndTheNextOne() {
        announcer.say(go)
        prepareAndDeliver()
        announcer.say(Cue.Speak(Speech.Rest, interrupt = true))
        prepareAndDeliver()
        val next = clips.created.last()

        main.advanceBy(ClipAnnouncer.FALLBACK_MS - 1) // the first clip's fallback would have fired here
        assertFalse(next.released)
        assertTrue(announcer.isSpeaking)
    }

    // ---- The generation counter: stop() racing the worker -----------------------------------

    @Test
    fun stoppingBeforeTheWorkerStartsMeansNothingIsEvenBuilt() {
        announcer.say(go)
        announcer.stop()
        prepareAndDeliver()

        assertTrue(clips.created.isEmpty())
        assertEquals("no decoding work for a cue that is already superseded", 0, library.pcmCalls)
        assertFalse(announcer.isSpeaking)
    }

    @Test
    fun stoppingAfterTheWorkerFinishedButBeforeDeliveryReleasesTheLateClip() {
        announcer.say(go)
        worker.runAll() // clip built, delivery posted but not yet run
        val clip = clips.created.single()

        announcer.stop()
        main.runDue() // the stale delivery arrives

        assertTrue("a clip nobody will play must not leak", clip.released)
        assertFalse(clip.started)
        assertFalse(announcer.isSpeaking)
    }

    @Test
    fun aStaleDeliveryDoesNotDisturbTheAnnouncementThatReplacedIt() {
        announcer.say(go)
        worker.runAll()
        val stale = clips.created.single()

        announcer.say(Cue.Speak(Speech.GetReady, interrupt = true)) // bumps the generation
        main.runDue() // the stale delivery for "go" arrives first

        assertTrue(stale.released)
        assertTrue("the new announcement is still counted as in flight", announcer.isSpeaking)

        prepareAndDeliver()
        assertTrue(clips.created.last().started)
    }

    @Test
    fun stopIsIdempotentAndAlwaysSchedulesTheFocusRelease() {
        announcer.stop()
        announcer.stop()
        assertEquals(listOf(300L, 300L), focus.releaseAfterCalls)
        assertFalse(announcer.isSpeaking)
    }

    // ---- Audio focus: a call starts, or the answer is no ------------------------------------

    @Test
    fun withoutFocusTheAnnouncerStaysSilentAndQueuesNothing() {
        focus.grant = false
        assertFalse(announcer.say(go))
        assertEquals(0, worker.pending)
        assertFalse(announcer.isSpeaking)
    }

    @Test
    fun focusLostBetweenQueueingAndPlayingDropsTheClipInsteadOfTalkingOverTheCall() {
        announcer.say(go)
        worker.runAll()
        focus.grant = false // the call started while the clip was being built
        main.runDue()

        val clip = clips.created.single()
        assertFalse(clip.started)
        assertTrue(clip.released)
        assertFalse(announcer.isSpeaking)

        focus.grant = true // call over: the next cue works again
        assertTrue(announcer.say(rest))
        prepareAndDeliver()
        assertTrue(clips.created.last().started)
    }

    @Test
    fun focusLostWithSeveralQueuedClipsDrainsThemAllWithoutPlayingAny() {
        announcer.say(go)
        announcer.say(rest)
        announcer.say(getReady)
        worker.runAll()
        focus.grant = false
        main.runDue()

        assertTrue(clips.created.all { it.released && !it.started })
        assertFalse(announcer.isSpeaking)
    }

    // ---- Failures on unusual devices --------------------------------------------------------

    @Test
    fun aTrackThatCannotStartIsDroppedAndTheNextOneStillPlays() {
        clips.nextFailsToStart = true
        announcer.say(go)
        announcer.say(rest)
        prepareAndDeliver()
        val (broken, healthy) = clips.created

        assertTrue(broken.released)
        assertTrue(healthy.started)
    }

    @Test
    fun aMissingClipSkipsTheWholeAnnouncementWithoutTouchingFocusOrTheWorker() {
        library.missing += "rest"
        assertFalse(announcer.say(rest))
        assertEquals(0, focus.requests)
        assertEquals(0, worker.pending)
    }

    @Test
    fun aMissingClipInAnInterruptingCueStillSilencesTheOutgoingAnnouncement() {
        announcer.say(go)
        prepareAndDeliver()
        val playing = clips.created.single()

        library.missing += "get_ready"
        assertFalse(announcer.say(Cue.Speak(Speech.GetReady, interrupt = true)))

        assertTrue("the old phrase must not talk over the new segment", playing.released)
        assertFalse(announcer.isSpeaking)
    }

    @Test
    fun aClipThatFailsToDecodeFreesTheFocusInsteadOfHoldingItForever() {
        library.undecodable += "go"
        assertTrue(announcer.say(go))
        prepareAndDeliver()

        assertTrue(clips.created.isEmpty())
        assertFalse(announcer.isSpeaking)
        assertEquals(listOf(300L), focus.releaseAfterCalls)
    }

    @Test
    fun aTrackThatCannotBeCreatedFreesTheFocus() {
        clips.returnsNull = true
        announcer.say(go)
        prepareAndDeliver()
        assertFalse(announcer.isSpeaking)
        assertEquals(listOf(300L), focus.releaseAfterCalls)
    }

    @Test
    fun aFactoryThatThrowsIsContainedAndStillReportsBack() {
        clips.throws = true
        announcer.say(go)
        prepareAndDeliver() // must not throw on either thread
        assertFalse(announcer.isSpeaking)
        assertEquals(listOf(300L), focus.releaseAfterCalls)
    }

    @Test
    fun aClipThatFailsToReleaseDoesNotBreakStopOrTheRestOfTheQueue() {
        announcer.say(go)
        announcer.say(rest)
        prepareAndDeliver()
        val (playing, queued) = clips.created
        playing.failToRelease = true

        announcer.stop() // must not throw

        assertTrue(playing.released)
        assertTrue(queued.released)
        assertFalse(announcer.isSpeaking)
    }

    // ---- Lifecycle ---------------------------------------------------------------------------

    @Test
    fun releaseStopsEverythingShutsTheWorkerDownAndRefusesNewCues() {
        announcer.say(go)
        announcer.say(rest)
        prepareAndDeliver()
        announcer.say(getReady) // in flight at release time

        announcer.release()

        assertTrue(clips.created.all { it.released })
        assertTrue(worker.isShutdown)
        assertFalse(announcer.isSpeaking)
        assertFalse(announcer.say(go))
        main.runDue() // a late delivery after release must be harmless
        assertEquals(0, main.pending)
    }

    @Test
    fun aDeliveryThatArrivesAfterReleaseFreesItsClip() {
        announcer.say(go)
        worker.runAll()
        announcer.release()
        main.runDue()
        assertTrue(clips.created.single().released)
        assertFalse(clips.created.single().started)
    }

    @Test
    fun prepareOnlyPreloadsTheClipsTheSpeechesNeed() {
        announcer.prepare(listOf(Speech.Go, Speech.Go, Speech.Rest))
        assertEquals(listOf(listOf("go", "rest")), library.preloaded.map { it.toList() })
    }

    @Test
    fun anEmptyCueIsNotAnnounced() {
        assertFalse(announcer.say(Cue.Speak(emptyList())))
        assertEquals(0, focus.requests)
        assertNull(clips.created.firstOrNull())
    }
}
