package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private fun work(s: Int) = Task(type = TaskType.WORK, durationSeconds = s)
    private fun rest(s: Int) = Task(type = TaskType.REST, durationSeconds = s)
    private fun EngineResult.spoken() = cues.filterIsInstance<Cue.Speak>().map { it.text }

    /** Ticks the engine every [step] ms until it stops running and returns everything spoken. */
    private fun run(engine: TimerEngine, until: Long = 600_000, step: Long = 10): List<String> {
        val spoken = ArrayList<String>()
        var t = 0L
        while (t <= until && engine.status == TimerStatus.RUNNING) {
            spoken += engine.tick(t).spoken()
            t += step
        }
        return spoken
    }

    @Test
    fun workIsNumberedPerRound_andRestIsPlain() {
        val engine = TimerEngine(
            TimerConfig(rounds = 2, tasks = listOf(work(5), rest(3), work(5)))
        )
        engine.start(0)
        val spoken = run(engine).filter { it.startsWith("Work") || it == "Rest" }
        assertEquals(listOf("Work 1", "Rest", "Work 2", "Work 1", "Rest", "Work 2"), spoken)
    }

    @Test
    fun zeroDurationItemsNeverStallTheTimer() {
        val engine = TimerEngine(
            TimerConfig(sets = 2, tasks = listOf(work(0), rest(2)))
        )
        engine.start(0)
        run(engine, until = 60_000)
        assertEquals(TimerStatus.FINISHED, engine.status)
    }

    @Test
    fun finishesExactlyAtPlannedTime_withoutDrift() {
        val config = TimerConfig(sets = 2, rounds = 3, setRestSeconds = 7, tasks = listOf(work(11), rest(4)))
        val engine = TimerEngine(config)
        engine.start(0)
        var t = 0L
        while (engine.status == TimerStatus.RUNNING) { engine.tick(t); t += 10 }
        val expected = (TimerEngine.DEFAULT_READY_SECONDS + config.totalDurationSeconds) * 1000L
        assertTrue("finished at $t, expected ~$expected", t in expected..(expected + 20))
    }

    @Test
    fun skipWhilePausedDoesNotCorruptTheNextItem() {
        val engine = TimerEngine(TimerConfig(tasks = listOf(work(30), rest(20))))
        engine.start(0)
        engine.tick(10_000)
        engine.pause(14_000)
        val skipped = engine.skip(20_000)
        assertEquals(Phase.REST, skipped.state.phase)
        engine.resume(50_000)
        assertEquals(20, engine.snapshot(50_000).remainingSeconds)
        assertEquals(15, engine.snapshot(55_000).remainingSeconds)
    }

    @Test
    fun lateTickAnnouncesOnlyTheItemItLandsOn() {
        val engine = TimerEngine(TimerConfig(tasks = listOf(work(5), rest(5))))
        engine.start(0)
        val late = engine.tick(15_900)
        assertEquals(Phase.REST, late.state.phase)
        assertEquals(900L, late.state.segmentElapsedMs)
        assertEquals(listOf("Rest"), late.spoken())
    }

    @Test
    fun forTime_lapsAndFinishSet() {
        val engine = TimerEngine(
            TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, setRestSeconds = 5)
        )
        engine.start(0)
        engine.tick(10_000)
        engine.lap(70_000)
        engine.lap(130_000)
        assertEquals(3, engine.snapshot(131_000).currentRound)
        val finished = engine.skip(140_000)
        assertEquals(Phase.SET_REST, finished.state.phase)
        assertEquals(listOf(60_000L, 60_000L, 10_000L), finished.state.laps.map { it.durationMs })
    }

    @Test
    fun forTime_roundThenFinishDoesNotCreateGhostLap() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME))
        engine.start(0)
        engine.tick(10_000)
        engine.lap(40_000)
        assertEquals(1, engine.skip(40_400).state.laps.size)
    }

    @Test
    fun forTime_timeCapEndsTheSet() {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME, timeCap = 20))
        engine.start(0)
        run(engine, until = 60_000)
        assertEquals(TimerStatus.FINISHED, engine.status)
        assertEquals(20_000L, engine.snapshot(60_000).laps.single().durationMs)
    }

    @Test
    fun lapIsIgnoredInTimerMode() {
        val engine = TimerEngine(TimerConfig())
        engine.start(0)
        assertTrue(engine.lap(1_000).state.laps.isEmpty())
    }
}
