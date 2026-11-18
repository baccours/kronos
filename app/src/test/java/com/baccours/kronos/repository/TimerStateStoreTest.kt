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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerStateStoreTest {

    @Test
    fun startsIdleAndFollowsUpdatesAndReset() {
        val store = TimerStateStore()
        assertEquals(TimerState(), store.timerState.value)

        val running = TimerState(status = TimerStatus.RUNNING, segmentIndex = 3)
        store.updateState(running)
        assertEquals(running, store.timerState.value)

        store.reset()
        assertEquals(TimerState(), store.timerState.value)
    }

    @Test
    fun instancesAreIsolatedFromEachOtherAndFromTheProcessWideOne() {
        val a = TimerStateStore()
        val b = TimerStateStore()
        a.updateState(TimerState(status = TimerStatus.RUNNING))
        assertEquals(TimerStatus.NOT_STARTED, b.timerState.value.status)
        assertNotSame(a, TimerStateStore)
        assertEquals(TimerStatus.NOT_STARTED, TimerStateStore.timerState.value.status)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(TimerStateStore is TimerStateStore)
    }

    @Test
    fun startFailureReachesAnActiveCollectorEachTime() = runTest {
        val store = TimerStateStore()
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
        val store = TimerStateStore()
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
