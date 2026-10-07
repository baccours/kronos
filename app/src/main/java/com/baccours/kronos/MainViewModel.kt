package com.baccours.kronos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.model.TimerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val timerStatus: TimerStatus = TimerStatus.NOT_STARTED
)

class MainViewModel(
    repository: TimerRepository = TimerRepository
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = repository.timerState
        .map { state ->
            MainUiState(timerStatus = state.status)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainUiState(repository.timerState.value.status)
        )
}
