package com.baccours.kronos.ui.running

import com.baccours.kronos.repository.TimerStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutViewModelTest {

    @Test
    fun startFailuresForwardsEventsFromTimerStateStore() = runBlocking {
        val store = TimerStateStore
        val viewModel = WorkoutViewModel(store)

        var emitted = false
        val job = launch(Dispatchers.Unconfined) {
            viewModel.startFailures.first()
            emitted = true
        }

        store.reportStartFailure()
        job.join()

        assertTrue(emitted)
    }
}
