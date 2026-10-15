package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipCatalogTest {

    private fun clips(vararg parts: Speech) = ClipCatalog.clipsFor(Cue.Speak(*parts))

    @Test
    fun upToTheLimitHasDedicatedClips() {
        assertEquals(10, ClipCatalog.MAX_DEDICATED)
        assertEquals(listOf("work01"), clips(Speech.Work(1)))
        assertEquals(listOf("work10"), clips(Speech.Work(10)))
        assertEquals(listOf("set02"), clips(Speech.SetNumber(2)))
        assertEquals(listOf("set10"), clips(Speech.SetNumber(10)))
        assertEquals(listOf("round03"), clips(Speech.RoundNumber(3)))
        assertEquals(listOf("round10"), clips(Speech.RoundNumber(10)))
    }

    @Test
    fun aboveTheLimitIsLabelPlusNumberWords() {
        assertEquals(listOf("work", "eleven"), clips(Speech.Work(11)))
        assertEquals(listOf("work", "twenty"), clips(Speech.Work(20)))
        assertEquals(listOf("work", "twenty", "one"), clips(Speech.Work(21)))
        assertEquals(listOf("work", "thirty"), clips(Speech.Work(30)))
        assertEquals(listOf("work", "forty", "two"), clips(Speech.Work(42)))
        assertEquals(listOf("set", "twenty", "nine"), clips(Speech.SetNumber(29)))
        assertEquals(listOf("round", "twelve"), clips(Speech.RoundNumber(12)))
        assertEquals(listOf("round", "ninety", "nine"), clips(Speech.RoundNumber(99)))
    }

    @Test
    fun hundreds() {
        assertEquals(listOf("work", "one", "hundred"), clips(Speech.Work(100)))
        assertEquals(listOf("work", "one", "hundred", "five"), clips(Speech.Work(105)))
        assertEquals(listOf("work", "one", "hundred", "ten"), clips(Speech.Work(110)))
        assertEquals(listOf("work", "one", "hundred", "twenty"), clips(Speech.Work(120)))
        assertEquals(listOf("work", "one", "hundred", "twenty", "five"), clips(Speech.Work(125)))
        assertEquals(listOf("work", "two", "hundred"), clips(Speech.Work(200)))
        assertEquals(listOf("work", "nine", "hundred", "ninety", "nine"), clips(Speech.Work(999)))
    }

    @Test
    fun beyondTheLastNumberIsJustTheLabel() {
        assertEquals(listOf("work"), clips(Speech.Work(1000)))
        assertEquals(listOf("work"), clips(Speech.Work(0)))
    }

    @Test
    fun setOneAndRoundOneFallBackToTheNumberWord() {
        // Never spoken by the engine; no dedicated clip exists, so they use the generic words.
        assertEquals(listOf("set", "one"), clips(Speech.SetNumber(1)))
        assertEquals(listOf("round", "one"), clips(Speech.RoundNumber(1)))
    }

    @Test
    fun anAnnouncementIsItsPartsInOrder() {
        assertEquals(
            listOf("set03", "round02", "work07"),
            clips(Speech.SetNumber(3), Speech.RoundNumber(2), Speech.Work(7))
        )
    }

    @Test
    fun phrases() {
        assertEquals(listOf("get_ready"), clips(Speech.GetReady))
        assertEquals(listOf("go"), clips(Speech.Go))
        assertEquals(listOf("rest"), clips(Speech.Rest))
        assertEquals(listOf("rest_between_sets"), clips(Speech.RestBetweenSets))
        assertEquals(listOf("halfway"), clips(Speech.Halfway))
        assertEquals(listOf("ten_seconds"), clips(Speech.TenSeconds))
        assertEquals(listOf("well_done"), clips(Speech.WellDone))
    }

    @Test
    fun clipsForAllListsClipsInFirstUseOrderWithoutRepeats() {
        val list = ClipCatalog.clipsForAll(
            listOf(Speech.GetReady, Speech.Work(1), Speech.Rest, Speech.Work(2), Speech.Rest, Speech.Work(1))
        )
        assertEquals(listOf("get_ready", "work01", "rest", "work02"), list)
    }

    @Test
    fun allClipsIsTheCompleteShoppingList() {
        val all = ClipCatalog.allClips
        val n = ClipCatalog.MAX_DEDICATED
        // 7 phrases + 3 labels + work01..N + set02..N + round02..N
        // + one..twenty + thirty..ninety + hundred
        assertEquals(7 + 3 + n + (n - 1) + (n - 1) + 20 + 7 + 1, all.size)
        assertEquals(all.size, all.toSet().size)
        assertTrue("set01" !in all && "round01" !in all)
        assertTrue("hundred" in all && "work" in all && "set" in all && "round" in all)
        // Valid file names: lower case, starts with a letter.
        assertTrue(all.all { Regex("[a-z][a-z0-9_]*").matches(it) })
    }
}
