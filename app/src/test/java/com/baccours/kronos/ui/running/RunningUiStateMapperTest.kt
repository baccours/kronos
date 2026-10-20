package com.baccours.kronos.ui.running

import com.baccours.kronos.engine.TimerEngine
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningUiStateMapperTest {

    /** Test fixture for FOR_TIME workout in active set. */
    private fun forTimeEngine(): TimerEngine {
        val engine = TimerEngine(TimerConfig(mode = TimerMode.FOR_TIME))
        engine.start(0)
        engine.tick(10_000)
        return engine
    }

    @Test
    fun timerModeShowsTheRemainingTime() {
        val engine = TimerEngine(
            TimerConfig(tasks = listOf(Task(type = TaskType.WORK, durationSeconds = 30)))
        )
        engine.start(0)
        engine.tick(10_000)
        val ui = RunningUiStateMapper().map(engine.tick(12_000).state)
        assertEquals("00:28", ui.displayTime)
        assertEquals(2, ui.elapsedSeconds)
    }

    @Test
    fun lapRowsAreRebuiltOnlyWhenALapIsLogged() {
        val engine = forTimeEngine()
        val mapper = RunningUiStateMapper()
        engine.lap(15_000)
        val first = mapper.map(engine.snapshot(16_000))
        val second = mapper.map(engine.snapshot(17_000))
        assertTrue("same rows while nothing changed", first.laps === second.laps)

        engine.lap(22_000)
        val third = mapper.map(engine.snapshot(23_000))
        assertEquals(2, third.laps.size)
        assertFalse(third.laps === second.laps)
    }

    @Test
    fun lapRowsAreNewestFirstWithTheDeltaToThePreviousLap() {
        val engine = forTimeEngine()
        engine.lap(15_000) // 5 s
        engine.lap(22_000) // 7 s
        val ui = RunningUiStateMapper().map(engine.snapshot(23_000))
        assertEquals(listOf(2, 1), ui.laps.map { it.number })
        assertEquals("+00:02", ui.laps[0].delta)
        assertFalse(ui.laps[0].isFaster)
        assertNull(ui.laps[1].delta)
    }

    @Test
    fun aFasterLapIsMarkedWithAMinus() {
        val engine = forTimeEngine()
        engine.lap(17_000) // 7 s
        engine.lap(22_000) // 5 s
        val ui = RunningUiStateMapper().map(engine.snapshot(23_000))
        assertEquals("\u221200:02", ui.laps[0].delta)
        assertTrue(ui.laps[0].isFaster)
    }

    @Test
    fun setResultsAreOnlyBuiltOnceTheWorkoutIsFinished() {
        val engine = forTimeEngine()
        val mapper = RunningUiStateMapper()
        engine.lap(15_000) // 5 s
        engine.lap(22_000) // 7 s
        assertTrue(mapper.map(engine.snapshot(23_000)).summaries.isEmpty())

        val finished = mapper.map(engine.skip(30_000).state) // closing lap: 8 s
        assertTrue(finished.isFinished)
        val summary = finished.summaries.single()
        assertEquals(3, summary.laps.size)
        assertEquals("00:20", summary.total)
    }

    @Test
    fun withoutAConfigThereIsNothingToShow() {
        val ui = RunningUiStateMapper().map(com.baccours.kronos.model.TimerState())
        assertEquals("00:00", ui.displayTime)
        assertTrue(ui.laps.isEmpty())
    }
}
