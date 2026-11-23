package com.baccours.kronos.engine

import com.baccours.kronos.model.Lap
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.UpNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the engine reports (the state snapshots) and how it treats pause, skip and restart.
 * [TimerEngineTest] covers the cues and the transitions; this covers the numbers the UI shows.
 */
class TimerEngineStateTest {

    private fun work(s: Int = 5) = Task(type = TaskType.WORK, durationSeconds = s)
    private fun rest(s: Int = 3) = Task(type = TaskType.REST, durationSeconds = s)
    private fun EngineResult.spoken() = cues.filterIsInstance<Cue.Speak>().map { it.text }

    /** READY 10 s, WORK 5 s, REST 3 s: the workout part lasts 8 s. */
    private val simple = TimerConfig(tasks = listOf(work(), rest()))
    private val ready = WorkoutPlan.READY_SECONDS * 1000L

    private fun started(config: TimerConfig = simple) = TimerEngine(config).also { it.start(0) }

    // ---- Get ready and what is up next -------------------------------------------------------

    @Test
    fun theWorkoutStartsWithAGetReadyCountdownAndPreviewsTheFirstItem() {
        val engine = TimerEngine(simple)
        val state = engine.start(0).state
        assertEquals(Phase.READY, state.phase)
        assertEquals(10, state.segmentSeconds)
        assertEquals(10, state.remainingSeconds)
        assertEquals(UpNext(Phase.WORK, workIndex = 1, set = 1, seconds = 5), state.next)
        assertEquals(7, engine.tick(3_000).state.remainingSeconds)
    }

    @Test
    fun nextIsNullOnTheLastItemAndOnceFinished() {
        val engine = started()
        assertEquals(UpNext(Phase.REST, 0, 1, 3), engine.tick(ready).state.next)
        val last = engine.tick(ready + 5_000).state
        assertEquals(Phase.REST, last.phase)
        assertNull(last.next)
        val done = engine.tick(ready + 8_000).state
        assertEquals(TimerStatus.FINISHED, done.status)
        assertNull(done.next)
    }

    // ---- Total remaining / elapsed -----------------------------------------------------------

    @Test
    fun totalRemainingCountsDownOnlyDuringTheWorkoutNotTheGetReady() {
        val engine = started()
        assertEquals(8, engine.snapshot(0).totalRemainingSeconds)
        assertEquals(8, engine.tick(9_000).state.totalRemainingSeconds) // still getting ready
        assertEquals(0, engine.snapshot(9_000).totalElapsedSeconds)

        assertEquals(8, engine.tick(ready).state.totalRemainingSeconds)
        val work2s = engine.tick(ready + 2_000).state
        assertEquals(6, work2s.totalRemainingSeconds)
        assertEquals(2, work2s.totalElapsedSeconds)

        val rest0s = engine.tick(ready + 5_000).state
        assertEquals(3, rest0s.totalRemainingSeconds)
        assertEquals(5, rest0s.totalElapsedSeconds)

        val done = engine.tick(ready + 8_000).state
        assertEquals(0, done.totalRemainingSeconds)
        assertEquals(8, done.totalElapsedSeconds)
    }

    @Test
    fun skippingDropsTheSkippedTimeFromWhatIsLeft() {
        val engine = started()
        engine.tick(ready)
        val afterSkip = engine.skip(ready + 1_000).state // 1 s into the 5 s work item
        assertEquals(Phase.REST, afterSkip.phase)
        assertEquals(3, afterSkip.totalRemainingSeconds)
        assertEquals(1, afterSkip.totalElapsedSeconds)
    }

    @Test
    fun skippingTheGetReadyStartsTheFirstItemWithItsAnnouncementAndNoElapsedTime() {
        val engine = started()
        val result = engine.skip(2_000)
        assertEquals(Phase.WORK, result.state.phase)
        assertEquals(0, result.state.totalElapsedSeconds)
        assertTrue(Cue.StartBuzzer in result.cues)
        assertEquals(listOf("Work 1"), result.spoken())
        // The new item runs its full length from the moment of the skip.
        assertEquals(TimerStatus.RUNNING, engine.tick(2_000 + 4_999).state.status)
        assertEquals(Phase.REST, engine.tick(2_000 + 5_000).state.phase)
    }

    @Test
    fun skippingTheLastItemFinishesWithTheCelebration() {
        val engine = started()
        engine.tick(ready + 5_000) // REST, the last item
        val result = engine.skip(ready + 6_000)
        assertEquals(TimerStatus.FINISHED, result.state.status)
        assertEquals(listOf(Cue.Celebration, Cue.Speak(Speech.WellDone, interrupt = true)), result.cues)
    }

    // ---- Pause and resume --------------------------------------------------------------------

    @Test
    fun pausedTimeIsNotCountedAndTheWorkoutEndsLaterByExactlyThePause() {
        val engine = started()
        engine.tick(ready + 2_000) // 2 s into WORK
        engine.pause(ready + 2_000)

        // Frozen while paused, however late it is looked at.
        val frozen = engine.snapshot(ready + 2_000)
        assertEquals(frozen, engine.snapshot(ready + 62_000))
        assertEquals(TimerStatus.PAUSED, frozen.status)
        assertEquals(2_000L, frozen.segmentElapsedMs)

        engine.resume(ready + 62_000) // a one-minute pause
        assertEquals(3, engine.tick(ready + 63_000).state.totalElapsedSeconds)

        val endsAt = ready + 8_000 + 60_000
        assertEquals(TimerStatus.RUNNING, engine.tick(endsAt - 1).state.status)
        assertEquals(TimerStatus.FINISHED, engine.tick(endsAt).state.status)
    }

    @Test
    fun ticksWhilePausedPlayNothingAndChangeNothing() {
        val engine = started()
        engine.tick(ready + 2_000)
        engine.pause(ready + 2_000)
        val result = engine.tick(ready + 30_000)
        assertTrue(result.cues.isEmpty())
        assertEquals(TimerStatus.PAUSED, result.state.status)
        assertEquals(2_000L, result.state.segmentElapsedMs)
    }

    @Test
    fun pausingTwiceKeepsTheFirstPauseInstant() {
        val engine = started()
        engine.tick(ready + 2_000)
        engine.pause(ready + 2_000)
        engine.pause(ready + 30_000) // ignored: must not move the pause instant
        engine.resume(ready + 40_000)
        assertEquals(2_000L, engine.snapshot(ready + 40_000).segmentElapsedMs)
    }

    @Test
    fun resumingARunningWorkoutChangesNothing() {
        val engine = started()
        engine.tick(ready + 2_000)
        val result = engine.resume(ready + 50_000)
        assertTrue(result.cues.isEmpty())
        assertEquals(TimerStatus.RUNNING, result.state.status)
        assertEquals(2_000L, engine.snapshot(ready + 2_000).segmentElapsedMs) // start was not shifted
    }

    // ---- Total caps --------------------------------------------------------------------------

    @Test
    fun forTimeShowsTimeLeftOnlyWithATotalCapAndOnlyForSeveralSets() {
        val open = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, sets = 1, totalTimeCap = 100))
        open.start(0)
        assertNull(open.tick(ready + 30_000).state.totalRemainingSeconds) // one set: the cap does not apply

        val capped = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, totalTimeCap = 100))
        capped.start(0)
        assertEquals(100, capped.snapshot(0).totalRemainingSeconds)
        assertEquals(70, capped.tick(ready + 30_000).state.totalRemainingSeconds)
        val done = capped.tick(ready + 100_000).state
        assertEquals(TimerStatus.FINISHED, done.status)
        assertEquals(0, done.totalRemainingSeconds)
    }

    @Test
    fun forTimeWithoutACapIsOpenEndedEvenWhenFinished() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, sets = 1))
        engine.start(0)
        engine.tick(ready)
        assertNull(engine.snapshot(ready + 5_000).totalRemainingSeconds)
        val done = engine.skip(ready + 5_000).state
        assertEquals(TimerStatus.FINISHED, done.status)
        assertNull(done.totalRemainingSeconds)
    }

    @Test
    fun aTimerIgnoresTheTotalCapOfAForTimeSetup() {
        // The setup keeps the cap when the user flips the mode; a Timer must still run to its end.
        val config = TimerConfig(sets = 2, totalTimeCap = 5, tasks = listOf(work()))
        val engine = TimerEngine(config)
        engine.start(0)
        assertEquals(10, engine.snapshot(0).totalRemainingSeconds)
        assertEquals(TimerStatus.RUNNING, engine.tick(ready + 9_999).state.status)
        assertEquals(TimerStatus.FINISHED, engine.tick(ready + 10_000).state.status)
    }

    // ---- For Time laps -----------------------------------------------------------------------

    @Test
    fun forTimeSkipClosesTheLapAndTheLapNumbersRestartInTheNextSet() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, sets = 2))
        engine.start(0)
        engine.tick(ready)

        val lap = engine.lap(ready + 5_000)
        assertEquals(listOf(Cue.Beep), lap.cues)
        assertEquals(2, lap.state.currentLap) // the lap being timed now
        assertEquals(1, engine.snapshot(ready + 6_000).lapElapsedSeconds)

        val skipped = engine.skip(ready + 8_000)
        assertEquals(2, skipped.state.currentSet)
        assertEquals(1, skipped.state.currentLap)
        assertEquals(listOf(Lap(1, 1, 5_000), Lap(1, 2, 3_000)), skipped.state.laps)
        assertEquals(listOf("Set 2. Go"), skipped.spoken())

        val second = engine.lap(ready + 10_000)
        assertEquals(Lap(2, 1, 2_000), second.state.laps.last())
        assertEquals(10, second.state.totalElapsedSeconds) // 8 s of set 1 + 2 s of set 2
    }

    @Test
    fun lapsAreIgnoredOutsideARunningWorkSegment() {
        val config = TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, setRestSeconds = 5, timeCap = 3)
        val engine = TimerEngine(config)
        engine.start(0)

        assertTrue(engine.lap(2_000).cues.isEmpty()) // while getting ready
        assertTrue(engine.snapshot(2_000).laps.isEmpty())

        engine.tick(ready)
        engine.pause(ready + 1_000)
        assertTrue(engine.lap(ready + 1_500).cues.isEmpty()) // while paused
        engine.resume(ready + 1_500)

        val restState = engine.tick(ready + 4_500).state // the 3 s cap, shifted by the 0.5 s pause
        assertEquals(Phase.SET_REST, restState.phase)
        val lapsBefore = restState.laps
        assertTrue(engine.lap(ready + 5_000).cues.isEmpty()) // while resting between sets
        assertEquals(lapsBefore, engine.snapshot(ready + 5_000).laps)
    }

    // ---- Tick alignment ----------------------------------------------------------------------

    @Test
    fun theNextTickLandsJustAfterTheNextWholeSecondOfTheSegment() {
        val engine = TimerEngine(simple)
        assertEquals(1000L, engine.millisUntilNextTick(0)) // not started: idle polling

        engine.start(0)
        assertEquals(1_010L, engine.millisUntilNextTick(0))
        assertEquals(760L, engine.millisUntilNextTick(250))
        assertEquals(11L, engine.millisUntilNextTick(999))
        assertEquals(1_010L, engine.millisUntilNextTick(1_000)) // exactly on the second: wait for the next

        engine.tick(ready) // WORK starts: seconds are counted from here, not from the workout start
        assertEquals(710L, engine.millisUntilNextTick(ready + 300))
    }

    @Test
    fun noTicksAreNeededWhilePaused() {
        val engine = started()
        engine.pause(500)
        assertEquals(1000L, engine.millisUntilNextTick(700))
    }

    @Test
    fun aSegmentThatWasPausedJustPastItsEndEndsAtOnceOnResume() {
        val engine = started()
        engine.tick(ready) // WORK (5 s) starts
        engine.pause(ready + 5_001) // 1 ms past its end, before the tick ran
        engine.resume(ready + 6_000)

        assertEquals(10L, engine.millisUntilNextTick(ready + 6_000)) // not a second later
        assertEquals(Phase.REST, engine.tick(ready + 6_010).state.phase)
    }

    @Test
    fun theTickIsPulledForwardToLandOnTheTotalCapExpiry() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, totalTimeCap = 3))
        engine.start(0)
        engine.tick(ready)
        engine.skip(ready + 1_500) // set 2 starts with 1.5 s of the 3 s cap left
        val start = ready + 1_500
        // 0.2 s in: the next second boundary (0.8 s) comes before the cap (1.3 s): normal alignment.
        assertEquals(810L, engine.millisUntilNextTick(start + 200))
        // 1.2 s in: the next second boundary is 0.8 s away, but the cap ends in 0.3 s.
        assertEquals(310L, engine.millisUntilNextTick(start + 1_200))
        // Cap already reached: tick at once.
        assertEquals(10L, engine.millisUntilNextTick(start + 1_500))
    }

    // ---- Lifecycle edges ---------------------------------------------------------------------

    @Test
    fun startingAgainResetsEverything() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, sets = 2))
        engine.start(0)
        engine.tick(ready)
        engine.lap(ready + 4_000)
        engine.skip(ready + 6_000)

        val state = engine.start(100_000).state
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(0, state.segmentIndex)
        assertEquals(Phase.READY, state.phase)
        assertTrue(state.laps.isEmpty())
        assertEquals(1, state.currentSet)
        assertEquals(0, state.totalElapsedSeconds)
    }

    @Test
    fun everyCommandBeforeStartIsHarmless() {
        val engine = TimerEngine(simple)
        assertTrue(engine.tick(5_000).cues.isEmpty())
        assertTrue(engine.skip(5_000).cues.isEmpty())
        assertTrue(engine.lap(5_000).cues.isEmpty())
        assertTrue(engine.pause(5_000).cues.isEmpty())
        assertTrue(engine.resume(5_000).cues.isEmpty())
        assertEquals(TimerStatus.NOT_STARTED, engine.status)
        assertEquals(0, engine.snapshot(5_000).segmentIndex)
    }

    @Test
    fun aFinishedWorkoutIgnoresEveryCommand() {
        val engine = started()
        engine.tick(ready + 8_000)
        assertEquals(TimerStatus.FINISHED, engine.status)
        val frozen = engine.snapshot(ready + 8_000)

        assertTrue(engine.tick(ready + 20_000).cues.isEmpty())
        assertTrue(engine.skip(ready + 20_000).cues.isEmpty())
        assertTrue(engine.lap(ready + 20_000).cues.isEmpty())
        assertTrue(engine.pause(ready + 20_000).cues.isEmpty())
        assertTrue(engine.resume(ready + 20_000).cues.isEmpty())
        assertEquals(TimerStatus.FINISHED, engine.status)
        assertEquals(frozen, engine.snapshot(ready + 20_000))
    }
}
