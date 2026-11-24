package com.baccours.kronos.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Requires kotlinx.serialization runtime. */
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
    }

    @Test
    fun anUnknownEnumValueFallsBackToItsDefaultAndKeepsTheRest() {
        val decoded = TimerConfigCodec.decode("""{"mode":"NO_SUCH_MODE","sets":4}""")
        assertEquals(TimerMode.TIMER, decoded?.mode)
        assertEquals(4, decoded?.sets)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val decoded = TimerConfigCodec.decode("""{"sets":4,"somethingFromTheFuture":true}""")
        assertEquals(4, decoded?.sets)
    }

    @Test
    fun tasksSavedWithoutIdsGetUniqueOnes() {
        val decoded = TimerConfigCodec.decode("""{"tasks":[{"type":"WORK"},{"type":"REST"}]}""")
        val ids = decoded?.tasks?.map { it.id }.orEmpty()
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun aDecodedConfigIsSanitized() {
        val decoded = TimerConfigCodec.decode("""{"sets":0,"rounds":-3}""")
        assertEquals(1, decoded?.sets)
        assertEquals(1, decoded?.rounds)
    }
}
