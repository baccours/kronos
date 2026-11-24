package com.baccours.kronos.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Synthesizes sound effect PCM data at [PcmUtil.SAMPLE_RATE].
 */
object ToneSynth {

    /** Countdown beep tone. */
    fun beep(): ShortArray = tone(880.0, 250)

    /** Buzzer sound for phase transitions. */
    fun startBuzzer(): ShortArray = tone(440.0, 550)

    /** Victory arpeggio tone for workout completion. */
    fun celebration(): ShortArray = PcmUtil.concat(
        listOf(tone(523.25, 150), tone(659.25, 150), tone(783.99, 150), tone(1046.5, 400))
    )

    /**
     * Generates a square wave PCM tone of [freqHz] with anti-click fading and target RMS scaling.
     */
    fun tone(freqHz: Double, durationMs: Int, targetRms: Double = 0.30, fadeMs: Int = 10): ShortArray {
        val rate = PcmUtil.SAMPLE_RATE
        val total = rate * durationMs / 1000
        val fade = (rate * fadeMs / 1000).coerceAtMost(total / 2)

        val samples = DoubleArray(total) { i ->
            var s = if (sin(2 * PI * freqHz * (i.toDouble() / rate)) >= 0) 1.0 else -1.0
            if (i < fade) s *= i.toDouble() / fade
            else if (i >= total - fade) s *= (total - i).toDouble() / fade
            s
        }

        val rms = sqrt(samples.sumOf { it * it } / total)
        val gain = if (rms > 0.0) targetRms / rms else 0.0

        return ShortArray(total) { i ->
            (samples[i] * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
