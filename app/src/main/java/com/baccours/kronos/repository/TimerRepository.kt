package com.baccours.kronos.repository

import com.baccours.kronos.model.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared workout state store interface. */
interface TimerStateStore {
    val timerState: StateFlow<TimerState>

    fun updateState(newState: TimerState)

    fun reset()
}

/** Global singleton state store for workout state. */
object TimerRepository : TimerStateStore {
    private val _timerState = MutableStateFlow(TimerState())
    override val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    override fun updateState(newState: TimerState) {
        _timerState.value = newState
    }

    override fun reset() {
        _timerState.value = TimerState()
    }
}
