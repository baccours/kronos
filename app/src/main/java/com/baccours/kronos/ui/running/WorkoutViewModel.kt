package com.baccours.kronos.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes workout UI state: [status] for top-level screen switching and [uiState] for the running view.
 *
 * Both flows are shared [SharingStarted.Eagerly]: with WhileSubscribed, the cached value goes stale
 * while the UI is stopped (> 5 s) and can be drawn for a frame on return, e.g. the setup screen after
 * the workout was stopped from the notification. The mapping is cheap, so keeping it hot costs nothing.
 */
class WorkoutViewModel(
    store: TimerStateStore
) : ViewModel() {

    val startFailures: Flow<Unit> = store.startFailures

    val status: StateFlow<TimerStatus> = store.timerState
        .map { it.status }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = store.timerState.value.status
        )

    private val mapper = RunningUiStateMapper()

    val uiState: StateFlow<RunningUiState> = store.timerState
        .map { mapper.map(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = mapper.map(store.timerState.value)
        )
}
