package com.baccours.kronos.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import com.baccours.kronos.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How [TimerAudioManager] routes cues according to the settings: a muted voice must neither be
 * spoken nor preloaded, and a change of settings applies to the very next cue. The announcer is
 * a fake, so nothing is played aloud.
 */
@RunWith(AndroidJUnit4::class)
class TimerAudioManagerInstrumentedTest {

    private class FakeAnnouncer : Announcer {
        val said = ArrayList<Cue.Speak>()
        val prepared = ArrayList<List<Speech>>()
        var stops = 0
        var releases = 0
        override val isSpeaking = false
        override fun say(cue: Cue.Speak): Boolean { said += cue; return true }
        override fun prepare(speeches: List<Speech>) { prepared += listOf(speeches) }
        override fun stop() { stops++ }
        override fun release() { releases++ }
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val announcer = FakeAnnouncer()
    private var settings = AppSettings()

    /** The manager is documented as main-thread only. */
    private fun onMain(block: (TimerAudioManager) -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val manager = TimerAudioManager(context, settings = { settings }, announcerFactory = { announcer })
            try {
                block(manager)
            } catch (t: Throwable) {
                failure = t
            } finally {
                manager.release()
            }
        }
        failure?.let { throw it }
    }

    private val goCue = Cue.Speak(Speech.GetReady)

    @Test
    fun aMutedVoiceIsNeitherSpokenNorPreloaded() {
        settings = AppSettings(voice = false)
        onMain { manager ->
            manager.prepare(listOf(Speech.GetReady))
            manager.play(goCue)
        }
        assertTrue(announcer.said.isEmpty())
        assertTrue(announcer.prepared.isEmpty())
    }

    @Test
    fun anActiveVoiceIsSpokenAndPreloaded() {
        settings = AppSettings(voice = true)
        onMain { manager ->
            manager.prepare(listOf(Speech.GetReady))
            manager.play(goCue)
        }
        assertEquals(listOf(goCue), announcer.said)
        assertEquals(listOf(listOf<Speech>(Speech.GetReady)), announcer.prepared)
    }

    @Test
    fun aSettingsChangeAppliesToTheNextCueWithoutRecreatingAnything() {
        settings = AppSettings(voice = false)
        onMain { manager ->
            manager.play(goCue)
            settings = settings.copy(voice = true)
            manager.play(goCue)
            settings = settings.copy(voice = false)
            manager.play(goCue)
        }
        assertEquals("only the cue played while the voice was on", listOf(goCue), announcer.said)
    }

    @Test
    fun turningTheBeepsOffDoesNotSilenceTheVoice() {
        settings = AppSettings(beeps = false, voice = true)
        onMain { manager ->
            manager.play(Cue.Beep)
            manager.play(Cue.StartBuzzer)
            manager.play(Cue.Celebration)
            manager.play(goCue)
        }
        assertEquals(listOf(goCue), announcer.said)
    }

    @Test
    fun stopAndReleaseReachTheAnnouncer() {
        onMain { manager -> manager.stopSpeaking() }
        // release() is called once by the test helper, after the block.
        assertEquals(1, announcer.stops)
        assertEquals(1, announcer.releases)
    }
}
