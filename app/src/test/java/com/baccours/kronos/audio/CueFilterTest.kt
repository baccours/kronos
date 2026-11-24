package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import com.baccours.kronos.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class CueFilterTest {

    private val tones = listOf(Cue.Beep, Cue.StartBuzzer, Cue.Celebration)
    private val speech = Cue.Speak(Speech.Work(1), interrupt = true)

    @Test
    fun everythingPlaysByDefault() {
        val settings = AppSettings()
        assertEquals(listOf(true, true, true, true), (tones + speech).map(settings::allows))
    }

    @Test
    fun voiceOffSilencesOnlySpeech() {
        val settings = AppSettings(voice = false)
        assertEquals(listOf(true, true, true, false), (tones + speech).map(settings::allows))
    }

    @Test
    fun beepsOffSilencesOnlyTones() {
        val settings = AppSettings(beeps = false)
        assertEquals(listOf(false, false, false, true), (tones + speech).map(settings::allows))
    }
}
