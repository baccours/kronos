package com.baccours.kronos.repository

import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutStateStoreTest {

    @Test
    fun startsIdleAndFollowsUpdatesAndReset() {
        val store = WorkoutStateStore()
        assertEquals(TimerState(), store.timerState.value)

        val running = TimerState(status = TimerStatus.RUNNING, segmentIndex = 3)
        store.updateState(running)
        assertEquals(running, store.timerState.value)

        store.reset()
        assertEquals(TimerState(), store.timerState.value)
    }

    @Test
    fun instancesAreIsolated() {
        val a = WorkoutStateStore()
        val b = WorkoutStateStore()
        a.updateState(TimerState(status = TimerStatus.RUNNING))
        assertEquals(TimerStatus.NOT_STARTED, b.timerState.value.status)
    }

    @Test
    fun startFailureReachesAnActiveCollectorEachTime() = runTest {
        val store = WorkoutStateStore()
        var received = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.startFailures.collect { received++ }
        }
        store.reportStartFailure()
        store.reportStartFailure()
        assertEquals(2, received)
        job.cancel()
    }

    @Test
    fun startFailureIsNotReplayedToALateCollector() = runTest {
        val store = WorkoutStateStore()
        store.reportStartFailure() // nobody listening: the event is dropped, not kept

        var received = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.startFailures.first()
            received = true
        }
        assertFalse(received)
        job.cancel()
    }
}
