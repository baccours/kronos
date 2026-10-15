package com.baccours.kronos.audio

import kotlin.math.roundToInt

/** Small PCM helpers for 16-bit samples. Pure Kotlin (no Android). */
object PcmUtil {

    /** Mixes interleaved [channels] down to mono by averaging them. */
    fun toMono(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return samples
        val frames = samples.size / channels
        return ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) sum += samples[f * channels + c]
            (sum / channels).toShort()
        }
    }

    /** Linear-interpolation resampling of mono samples from [fromRate] to [toRate]. */
    fun resample(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || samples.isEmpty()) return samples
        val outSize = (samples.size.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        val last = samples.size - 1
        return ShortArray(outSize) { i ->
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(last)
            val i1 = (i0 + 1).coerceAtMost(last)
            val frac = pos - i0
            (samples[i0] * (1 - frac) + samples[i1] * frac).roundToInt().toShort()
        }
    }

    fun concat(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }
}
