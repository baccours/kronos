package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The thresholds that decide when the workout beeps and speaks. */
class CuePolicyTest {

    private fun policy(totalCapMs: Long = 0) =
        CuePolicy(WorkoutPlan(TimerConfig()), isForTime = false, totalCapMs = totalCapMs)

    private fun segment(seconds: Int) = Segment(Phase.WORK, set = 1, round = 1, workIndex = 1, durationSeconds = seconds)

    private val tenSeconds = Cue.Speak(Speech.TenSeconds)
    private val halfway = Cue.Speak(Speech.Halfway)

    @Test
    fun theLastThreeSecondsBeep() {
        val p = policy()
        for (remaining in 1..3) assertEquals("at $remaining", Cue.Beep, p.countdownCues(segment(20), remaining))
        assertNull(p.countdownCues(segment(20), 4))
        assertNull(p.countdownCues(segment(20), 0))
    }

    @Test
    fun tenSecondsLeftIsOnlySpokenInItemsOfAtLeastThirtySeconds() {
        val p = policy()
        assertEquals(tenSeconds, p.countdownCues(segment(30), 10))
        assertNull(p.countdownCues(segment(29), 10))
        assertNull(p.countdownCues(segment(30), 11))
    }

    @Test
    fun halfwayIsOnlySpokenInItemsOfAtLeastAMinute() {
        val p = policy()
        assertEquals(halfway, p.countdownCues(segment(60), 30))
        assertEquals(halfway, p.countdownCues(segment(61), 30)) // integer half
        assertEquals(halfway, p.countdownCues(segment(120), 60))
        assertNull(p.countdownCues(segment(59), 29))
    }

    @Test
    fun aLongItemKeepsBothAnnouncementsAndTheBeeps() {
        val p = policy()
        assertEquals(halfway, p.countdownCues(segment(60), 30))
        assertEquals(tenSeconds, p.countdownCues(segment(60), 10))
        assertEquals(Cue.Beep, p.countdownCues(segment(60), 2))
    }

    @Test
    fun theTotalCapWarnsAtTenSecondsOnlyWhenTheCapIsAtLeastThirtySeconds() {
        assertEquals(tenSeconds, policy(totalCapMs = 30_000).totalCapCue(10_000))
        assertNull(policy(totalCapMs = 29_999).totalCapCue(10_000))
    }

    @Test
    fun theTotalCapBeepsDuringTheLastThreeSecondsRoundedUp() {
        val p = policy(totalCapMs = 60_000)
        assertEquals(Cue.Beep, p.totalCapCue(3_000))
        assertEquals(Cue.Beep, p.totalCapCue(2_000))
        assertEquals(Cue.Beep, p.totalCapCue(1_000))
        assertEquals(Cue.Beep, p.totalCapCue(1)) // a sliver left still counts as the last second
        assertNull(p.totalCapCue(3_001)) // 4 s left once rounded up
        assertEquals(tenSeconds, p.totalCapCue(9_001)) // 10 s left once rounded up
        assertNull(p.totalCapCue(10_001))
    }

    @Test
    fun noTotalCapWarningOnceTheCapIsReached() {
        val p = policy(totalCapMs = 60_000)
        assertNull(p.totalCapCue(0))
        assertNull(p.totalCapCue(-500))
    }
}
