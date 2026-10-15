package com.baccours.kronos.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmUtilTest {

    private fun shorts(vararg v: Int) = ShortArray(v.size) { v[it].toShort() }
    private fun ShortArray.list() = toList().map { it.toInt() }

    @Test
    fun monoIsLeftUntouched() {
        val mono = shorts(1, 2, 3)
        assertEquals(listOf(1, 2, 3), PcmUtil.toMono(mono, 1).list())
    }

    @Test
    fun stereoIsAveraged() {
        assertEquals(listOf(5, -5, 100), PcmUtil.toMono(shorts(0, 10, -10, 0, 100, 100), 2).list())
    }

    @Test
    fun sameRateIsUntouched() {
        val s = shorts(1, 2, 3)
        assertEquals(listOf(1, 2, 3), PcmUtil.resample(s, 48_000, 48_000).list())
    }

    @Test
    fun resamplingKeepsTheDuration() {
        val s = ShortArray(44_100) { (it % 100).toShort() } // 1 second at 44.1 kHz
        assertEquals(48_000, PcmUtil.resample(s, 44_100, 48_000).size)
        assertEquals(22_050, PcmUtil.resample(s, 44_100, 22_050).size)
    }

    @Test
    fun upsamplingInterpolatesLinearly() {
        assertEquals(listOf(0, 50, 100, 100), PcmUtil.resample(shorts(0, 100), 1, 2).list())
    }

    @Test
    fun concatKeepsOrder() {
        val out = PcmUtil.concat(listOf(shorts(1, 2), shorts(), shorts(3)))
        assertEquals(listOf(1, 2, 3), out.list())
        assertTrue(PcmUtil.concat(emptyList()).isEmpty())
    }
}
