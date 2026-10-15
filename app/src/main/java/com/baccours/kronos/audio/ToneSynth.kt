package com.baccours.kronos.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Synthesizes the app's beeps and buzzers as mono 16-bit PCM at [PcmUtil.SAMPLE_RATE].
 * Pure Kotlin (no Android), so it can be unit-tested.
 */
object ToneSynth {

    /** Countdown beep. */
    fun beep(): ShortArray = tone(880.0, 250)

    /** Start of a work or rest phase. Square wave = harsh "buzzer" timbre. */
    fun startBuzzer(): ShortArray = tone(440.0, 550)

    /** End of the workout: C major ascending arpeggio C5, E5, G5, C6. */
    fun celebration(): ShortArray = PcmUtil.concat(
        listOf(tone(523.25, 150), tone(659.25, 150), tone(783.99, 150), tone(1046.5, 400))
    )

    /**
     * A square wave of [freqHz] lasting [durationMs], with [fadeMs] fade-in and fade-out against
     * clicks, scaled so that its RMS level is [targetRms] (1.0 = full scale). Keep the target low
     * enough to avoid clipping.
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
