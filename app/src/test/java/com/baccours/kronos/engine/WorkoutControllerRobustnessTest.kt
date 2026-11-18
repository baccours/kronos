package com.baccours.kronos.engine

import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the controller behaves when its collaborators misbehave, and when it is driven out of order.
 * [WorkoutControllerTest] covers the normal flow.
 */
class WorkoutControllerRobustnessTest {

    private class Rig : TickScheduler, CuePlayer, WorkoutController.Listener {
        var now = 0L
        var dueAt = -1L
        var pending: (() -> Unit)? = null

        val states = ArrayList<TimerState>()
        val listenerErrors = ArrayList<String>()
        val lateTicks = ArrayList<Long>()
        var finishedCalls = 0
        var stopSpeakingCalls = 0
        var throwOnState = false
        var throwOnFinish = false

        override fun scheduleAfter(delayMs: Long, task: () -> Unit) { dueAt = now + delayMs; pending = task }
        override fun cancel() { pending = null }

        override fun play(cue: Cue) {}
        override fun prepare(speeches: List<Speech>) {}
        override fun stopSpeaking() { stopSpeakingCalls++ }
        override fun release() {}

        override fun onStateChanged(state: TimerState) {
            states += state
            if (throwOnState) throw IllegalStateException("ui")
        }
        override fun onFinished(state: TimerState) {
            finishedCalls++
            if (throwOnFinish) throw IllegalStateException("notification")
        }
        override fun onLateTick(lateMs: Long) { lateTicks += lateMs }
        override fun onListenerError(callback: String, error: Exception) { listenerErrors += callback }

        /** Runs the pending tick as if the scheduler fired it at [at]. */
        fun fire(at: Long) {
            now = at
            val task = pending ?: return
            pending = null
            task()
        }

        /** Fires every tick on time until nothing is scheduled any more. */
        fun runToTheEnd(limit: Int = 10_000) {
            var n = 0
            while (pending != null && n++ < limit) fire(dueAt)
        }

        fun controller() = WorkoutController(this, this, { now }, this)
    }

    private val config = TimerConfig(
        tasks = listOf(Task(type = TaskType.WORK, durationSeconds = 1), Task(type = TaskType.REST, durationSeconds = 1))
    )

    @Test
    fun aListenerThatThrowsOnEveryStateNeverStopsTheClock() {
        val rig = Rig().apply { throwOnState = true }
        val controller = rig.controller()

        assertTrue(controller.start(config)) // must not throw into the caller
        rig.runToTheEnd()

        assertEquals(TimerStatus.FINISHED, controller.status)
        assertEquals(1, rig.finishedCalls)
        assertTrue(rig.listenerErrors.isNotEmpty())
        assertTrue(rig.listenerErrors.all { it == "onStateChanged" })
    }

    @Test
    fun aThrowingFinishCallbackIsReportedAndTheTickerIsStillStopped() {
        val rig = Rig().apply { throwOnFinish = true }
        val controller = rig.controller()
        controller.start(config)
        rig.runToTheEnd()

        assertEquals(TimerStatus.FINISHED, controller.status)
        assertEquals(listOf("onFinished"), rig.listenerErrors)
        assertNull("nothing may stay scheduled after the end", rig.pending)
    }

    @Test
    fun aTickIsLateOnlyBeyondHalfASecond() {
        val rig = Rig()
        rig.controller().start(config)

        rig.fire(rig.dueAt + 500) // exactly the threshold: not reported
        assertTrue(rig.lateTicks.isEmpty())

        rig.fire(rig.dueAt + 501)
        assertEquals(listOf(501L), rig.lateTicks)
    }

    @Test
    fun stopForgetsTheWorkoutAndIgnoresAnyTickStillInFlight() {
        val rig = Rig()
        val controller = rig.controller()
        controller.start(config)
        rig.fire(rig.dueAt)
        val published = rig.states.size

        controller.stop()

        assertEquals(TimerStatus.NOT_STARTED, controller.status)
        assertFalse(controller.isActive)
        assertEquals(TimerState(), controller.lastState)
        assertNull(rig.pending)
        assertTrue(rig.stopSpeakingCalls >= 1)

        controller.onTick() // a tick that was already on its way when stop() ran
        assertEquals(published, rig.states.size)
    }

    @Test
    fun commandsWithoutAWorkoutDoNothing() {
        val rig = Rig()
        val controller = rig.controller()
        controller.pause()
        controller.resume()
        controller.skip()
        controller.lap()
        controller.prepareCues()

        assertTrue(rig.states.isEmpty())
        assertEquals(0, rig.stopSpeakingCalls)
        assertNull(rig.pending)
    }

    @Test
    fun pausingTwiceSilencesTheVoiceAndPublishesOnlyOnce() {
        val rig = Rig()
        val controller = rig.controller()
        controller.start(config)
        controller.pause()
        val published = rig.states.size
        val silenced = rig.stopSpeakingCalls

        controller.pause()

        assertEquals(published, rig.states.size)
        assertEquals(silenced, rig.stopSpeakingCalls)
        assertEquals(TimerStatus.PAUSED, controller.status)
    }

    @Test
    fun resumingARunningWorkoutPublishesNothing() {
        val rig = Rig()
        val controller = rig.controller()
        controller.start(config)
        val published = rig.states.size

        controller.resume()

        assertEquals(published, rig.states.size)
        assertEquals(TimerStatus.RUNNING, controller.status)
    }
}
