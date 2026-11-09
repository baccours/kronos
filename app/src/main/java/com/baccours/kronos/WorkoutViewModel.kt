package com.baccours.kronos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore
import com.baccours.kronos.ui.running.RunningUiState
import com.baccours.kronos.ui.running.RunningUiStateMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes workout UI state: [status] for top-level screen switching and [uiState] for the running view.
 */
class WorkoutViewModel(
    store: TimerStateStore = TimerStateStore
) : ViewModel() {

    val status: StateFlow<TimerStatus> = store.timerState
        .map { it.status }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = store.timerState.value.status
        )

    private val mapper = RunningUiStateMapper()

    val uiState: StateFlow<RunningUiState> = store.timerState
        .map { mapper.map(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = mapper.map(store.timerState.value)
        )
}
