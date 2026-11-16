package com.baccours.kronos.engine

import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test harness running engine and controller with virtual clock and scheduler. */
class WorkoutControllerTest {

    /** Simulates tick scheduling delay (jitter). */
    private class World(private val jitterMs: Long = 0) :
        TickScheduler, CuePlayer, WorkoutController.Listener {

        var now = 0L
        var dueAt = -1L
        private var pending: (() -> Unit)? = null

        val played = ArrayList<Cue>()
        val states = ArrayList<TimerState>()
        val lateTicks = ArrayList<Long>()
        val cueErrors = ArrayList<Cue>()
        var finished: TimerState? = null
        var finishedAt = -1L
        var finishedCount = 0
        val prepared = ArrayList<List<Speech>>()
        var stopSpeakingCalls = 0
        var failOnBeep = false

        val hasPending get() = pending != null

        fun advanceTo(target: Long) {
            while (true) {
                val task = pending ?: break
                if (dueAt > target) break
                now = maxOf(now, dueAt + jitterMs)
                pending = null
                task()
            }
            now = maxOf(now, target)
        }

        override fun scheduleAfter(delayMs: Long, task: () -> Unit) {
            dueAt = now + delayMs
            pending = task
        }

        override fun cancel() {
            pending = null
        }

        override fun play(cue: Cue) {
            if (failOnBeep && cue == Cue.Beep) throw IllegalStateException("boom")
            played += cue
        }

        override fun prepare(speeches: List<Speech>) { prepared += speeches }
        override fun stopSpeaking() { stopSpeakingCalls++ }
        override fun release() {}

        override fun onStateChanged(state: TimerState) { states += state }
        override fun onFinished(state: TimerState) { finished = state; finishedAt = now; finishedCount++ }
        override fun onLateTick(lateMs: Long) { lateTicks += lateMs }
        override fun onCueError(cue: Cue, error: Exception) { cueErrors += cue }

        fun controller() = WorkoutController(this, this, { now }, this)
    }

    private fun work(s: Int) = Task(type = TaskType.WORK, durationSeconds = s)
    private fun rest(s: Int) = Task(type = TaskType.REST, durationSeconds = s)

    private val simple = TimerConfig(tasks = listOf(work(3), rest(2)))
    private val plannedMs = (WorkoutPlan.READY_SECONDS + simple.totalDurationSeconds) * 1000L

    @Test
    fun aWorkoutRunsToTheEndOnItsOwn() {
        val world = World()
        val controller = world.controller()
        assertTrue(controller.start(simple))
        world.advanceTo(100_000)
        assertNotNull(world.finished)
        assertEquals(TimerStatus.FINISHED, controller.status)
        assertFalse(world.hasPending)
        assertTrue("finished at ${world.finishedAt}", world.finishedAt in plannedMs..(plannedMs + 50))
        assertEquals(Cue.Celebration, world.played.single { it == Cue.Celebration })
    }

    @Test
    fun skipAndLapAfterTheEndDoNotFinishAgain() {
        val world = World()
        val controller = world.controller()
        controller.start(simple)
        world.advanceTo(100_000)
        controller.skip()
        controller.lap()
        assertEquals(1, world.finishedCount)
    }

    @Test
    fun lateTicksDoNotChangeWhatIsPlayed() {
        val onTime = World()
        onTime.controller().start(simple)
        onTime.advanceTo(100_000)

        val late = World(jitterMs = 37)
        late.controller().start(simple)
        late.advanceTo(100_000)

        assertEquals(onTime.played, late.played)
        assertTrue(late.finishedAt in plannedMs..(plannedMs + 100))
    }

    @Test
    fun aVeryLateTickIsReported() {
        val world = World(jitterMs = 700)
        world.controller().start(simple)
        world.advanceTo(100_000)
        assertTrue(world.lateTicks.isNotEmpty())
        assertNotNull(world.finished)
    }

    @Test
    fun pauseStopsTheTickerAndResumeRestartsIt() {
        val world = World()
        val controller = world.controller()
        controller.start(simple)
        world.advanceTo(2_000)
        controller.pause()
        assertFalse(world.hasPending)
        assertEquals(1, world.stopSpeakingCalls)

        val statesWhilePaused = world.states.size
        world.advanceTo(10_000)
        assertEquals(statesWhilePaused, world.states.size)
        assertEquals(TimerStatus.PAUSED, controller.status)

        controller.resume()
        assertTrue(world.hasPending)
        world.advanceTo(100_000)
        val expected = plannedMs + 8_000
        assertTrue("finished at ${world.finishedAt}", world.finishedAt in expected..(expected + 50))
    }

    @Test
    fun skipRealignsTheTickerToTheNewSegment() {
        val world = World()
        val controller = world.controller()
        controller.start(TimerConfig(tasks = listOf(work(30), rest(30))))
        world.advanceTo(10_400)
        controller.skip()
        val untilNextTick = world.dueAt - world.now
        assertTrue("next tick in $untilNextTick ms", untilNextTick in 1000..1100)
    }

    @Test
    fun skippingTheLastItemFinishes() {
        val world = World()
        val controller = world.controller()
        controller.start(TimerConfig(tasks = listOf(work(30))))
        world.advanceTo(10_500)
        controller.skip()
        assertNotNull(world.finished)
        assertFalse(world.hasPending)
    }

    @Test
    fun aFailingCueNeitherStopsTheTimerNorTheStateUpdates() {
        val world = World().apply { failOnBeep = true }
        world.controller().start(simple)
        world.advanceTo(100_000)
        assertTrue(world.cueErrors.isNotEmpty())
        assertNotNull(world.finished)
    }

    @Test
    fun startIsIgnoredWhileAWorkoutIsActive_andAllowedAfterwards() {
        val world = World()
        val controller = world.controller()
        assertTrue(controller.start(simple))
        world.advanceTo(2_000)
        assertFalse(controller.start(simple))
        world.advanceTo(100_000)
        assertTrue(controller.start(simple))
    }

    @Test
    fun stopAbandonsTheWorkoutWithoutFinishing() {
        val world = World()
        val controller = world.controller()
        controller.start(simple)
        world.advanceTo(2_000)
        controller.stop()
        assertEquals(TimerStatus.NOT_STARTED, controller.status)
        assertFalse(world.hasPending)
        assertNull(world.finished)
    }

    @Test
    fun lapsAreLoggedInForTimeMode() {
        val world = World()
        val controller = world.controller()
        controller.start(TimerConfig(mode = TimerMode.FOR_TIME))
        world.advanceTo(10_500)
        controller.lap()
        assertEquals(1, controller.lastState.laps.size)
    }

    @Test
    fun prepareCuesReloadsTheSpeechOfTheActiveWorkoutOnly() {
        val world = World()
        val controller = world.controller()
        controller.prepareCues()
        assertTrue(world.prepared.isEmpty())

        controller.start(simple)
        controller.prepareCues()
        assertEquals(2, world.prepared.size)
        assertEquals(world.prepared[0], world.prepared[1])

        world.advanceTo(100_000)
        controller.prepareCues()
        assertEquals(2, world.prepared.size)
    }
}
