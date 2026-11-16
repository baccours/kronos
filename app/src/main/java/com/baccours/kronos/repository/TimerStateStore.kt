package com.baccours.kronos.repository

import com.baccours.kronos.model.TimerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide in-memory state store, shared by the service (writer) and the UI (reader). */
object TimerStateStore {
    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _startFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One-shot event: the service could not start the workout (e.g. foreground start refused). */
    val startFailures: SharedFlow<Unit> = _startFailures.asSharedFlow()

    fun reportStartFailure() {
        _startFailures.tryEmit(Unit)
    }

    fun updateState(newState: TimerState) {
        _timerState.value = newState
    }

    fun reset() {
        _timerState.value = TimerState()
    }
}
