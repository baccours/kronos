package com.baccours.kronos.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ToneSynthTest {

    private fun ShortArray.rms() = sqrt(sumOf { (it.toDouble() / Short.MAX_VALUE).let { v -> v * v } } / size)

    @Test
    fun durationsMatchTheSoundDesign() {
        val rate = PcmUtil.SAMPLE_RATE
        assertEquals(rate * 250 / 1000, ToneSynth.beep().size)
        assertEquals(rate * 550 / 1000, ToneSynth.startBuzzer().size)
        assertEquals(rate * (150 + 150 + 150 + 400) / 1000, ToneSynth.celebration().size)
    }

    @Test
    fun levelIsNormalizedToTheTargetRms() {
        for (pcm in listOf(ToneSynth.beep(), ToneSynth.startBuzzer(), ToneSynth.celebration())) {
            assertEquals(0.30, pcm.rms(), 0.01)
        }
        assertEquals(0.20, ToneSynth.tone(440.0, 200, targetRms = 0.20).rms(), 0.01)
    }

    @Test
    fun neverClips() {
        for (pcm in listOf(ToneSynth.beep(), ToneSynth.startBuzzer(), ToneSynth.celebration())) {
            assertTrue(pcm.all { it > Short.MIN_VALUE && it < Short.MAX_VALUE })
        }
    }

    @Test
    fun fadesInAndOutAgainstClicks() {
        val pcm = ToneSynth.beep()
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < peak / 20)
        // Fully faded in after 10 ms.
        val faded = PcmUtil.SAMPLE_RATE * 10 / 1000
        assertTrue(pcm.drop(faded).take(500).any { abs(it.toInt()) > peak * 9 / 10 })
    }

    @Test
    fun isASquareWaveOfTheRequestedFrequency() {
        val pcm = ToneSynth.tone(1000.0, 100)
        val crossings = (1 until pcm.size).count { (pcm[it - 1] < 0) != (pcm[it] < 0) }
        // 1 kHz for 0.1 s = 100 periods = about 200 sign changes.
        assertTrue(crossings in 190..210)
    }
}
