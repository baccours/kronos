package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The production wiring (`bindTo`) of a real [AudioFocusController] and a real [ClipAnnouncer],
 * over fake Android: the situations that are hard to reproduce by hand.
 */
class AudioInterruptionScenarioTest {

    private val main = FakeMainThread()
    private val worker = FakeWorker()
    private val system = FakeFocusSystem()
    private val clips = FakeClipFactory()
    private val focus = AudioFocusController(main, system)
    private val announcer = ClipAnnouncer(focus, FakeClipSource(), main, worker, clips)

    init { focus.bindTo(announcer) }

    private val go = Cue.Speak(Speech.Go)

    private fun speak(cue: Cue.Speak = go): FakeClip {
        assertTrue(announcer.say(cue))
        worker.runAll()
        main.runDue()
        return clips.created.last()
    }

    @Test
    fun aPhoneCallMidSentenceSilencesTheVoiceAtOnceAndGivesTheFocusBack() {
        val clip = speak()
        assertTrue(clip.started)

        system.loseFocus()

        assertTrue(clip.released)
        assertFalse(announcer.isSpeaking)
        assertEquals(1, system.abandons)
    }

    @Test
    fun duringTheCallCuesAreSilentAndAfterItTheyWorkAgain() {
        speak()
        system.loseFocus()

        system.grant = false // call in progress
        assertFalse(announcer.say(go))
        assertEquals(1, clips.created.size)

        system.grant = true // call ended
        assertTrue(speak().started)
    }

    @Test
    fun aCallWhileAClipIsStillBeingBuiltDropsThatClipToo() {
        announcer.say(go)
        worker.runAll() // built, delivery pending

        system.loseFocus()
        main.runDue()

        val clip = clips.created.single()
        assertTrue(clip.released)
        assertFalse(clip.started)
        assertFalse(announcer.isSpeaking)
    }

    @Test
    fun aBeepsReleaseTimerCannotDropTheFocusUnderSpeech() {
        val clip = speak()
        focus.releaseAfter(AudioFocusController.TAIL_MS) // what a finished beep schedules

        main.advanceBy(AudioFocusController.TAIL_MS)
        assertEquals("focus must be kept while the voice is speaking", 0, system.abandons)

        clip.reachLastFrame()
        main.advanceBy(ClipAnnouncer.OUTPUT_TAIL_MS + AudioFocusController.TAIL_MS)
        assertFalse(announcer.isSpeaking)
        assertEquals("and released once the voice is done", 1, system.abandons)
    }

    @Test
    fun theFocusIsAlwaysReleasedEventually() {
        speak().reachLastFrame()
        main.advanceBy(10_000)
        assertEquals(1, system.abandons)
        assertEquals(0, main.pending)
    }
}
