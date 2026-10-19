package com.baccours.kronos.repository

import com.baccours.kronos.model.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI and the service need from the shared workout state. Lets tests supply their own. */
interface TimerStateStore {
    val timerState: StateFlow<TimerState>

    fun updateState(newState: TimerState)

    fun reset()
}

/** Single source of truth shared between [com.baccours.kronos.service.TimerService] and the UI. */
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
