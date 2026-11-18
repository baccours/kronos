package com.baccours.kronos.ui.running

import com.baccours.kronos.MainDispatcherRule
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    /** `viewModelScope` needs a Main dispatcher, which a JVM test does not have. */
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun startFailuresForwardsEventsFromTimerStateStore() = runTest {
        val store = TimerStateStore()
        val viewModel = WorkoutViewModel(store)

        var emitted = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.startFailures.first()
            emitted = true
        }

        store.reportStartFailure()
        job.join()

        assertTrue(emitted)
    }

    @Test
    fun statusAndUiStateAreCurrentEvenWithNobodyCollecting() {
        // Regression: with WhileSubscribed the cached value went stale while the UI was stopped,
        // and the first frame after returning could show the previous screen.
        val store = TimerStateStore()
        val viewModel = WorkoutViewModel(store)
        assertEquals(TimerStatus.NOT_STARTED, viewModel.status.value)

        store.updateState(
            TimerState(status = TimerStatus.RUNNING, config = TimerConfig(), phase = Phase.WORK, workIndex = 2)
        )
        assertEquals(TimerStatus.RUNNING, viewModel.status.value)
        assertEquals(Phase.WORK, viewModel.uiState.value.phase)
        assertEquals(2, viewModel.uiState.value.workIndex)

        store.reset()
        assertEquals(TimerStatus.NOT_STARTED, viewModel.status.value)
    }

    @Test
    fun aWorkoutAlreadyRunningWhenTheViewModelIsCreatedIsShownAtOnce() {
        // The Activity is recreated while the service keeps running: no flash of the setup screen.
        val store = TimerStateStore()
        store.updateState(TimerState(status = TimerStatus.PAUSED, config = TimerConfig()))
        val viewModel = WorkoutViewModel(store)
        assertEquals(TimerStatus.PAUSED, viewModel.status.value)
        assertEquals(TimerStatus.PAUSED, viewModel.uiState.value.status)
    }
}
