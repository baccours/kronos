package com.baccours.kronos.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Needs kotlinx.serialization (the real one), so it runs under Gradle only. */
class TimerConfigCodecTest {

    @Test
    fun aConfigSurvivesAnEncodeDecodeRoundTrip() {
        val config = TimerConfig(mode = TimerMode.FOR_TIME, sets = 3, timeCap = 120, totalTimeCap = 600)
        assertEquals(config, TimerConfigCodec.decode(TimerConfigCodec.encode(config)))
    }

    @Test
    fun missingOrCorruptTextDecodesToNull() {
        assertNull(TimerConfigCodec.decode(null))
        assertNull(TimerConfigCodec.decode(""))
        assertNull(TimerConfigCodec.decode("{not json"))
        assertNull(TimerConfigCodec.decode("""{"mode":"NO_SUCH_MODE"}"""))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val decoded = TimerConfigCodec.decode("""{"sets":4,"somethingFromTheFuture":true}""")
        assertEquals(4, decoded?.sets)
    }

    @Test
    fun aDecodedConfigIsSanitized() {
        val decoded = TimerConfigCodec.decode("""{"sets":0,"rounds":-3}""")
        assertEquals(1, decoded?.sets)
        assertEquals(1, decoded?.rounds)
    }
}
