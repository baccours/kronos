package com.baccours.kronos.repository

import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TimerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerStatus {
    NOT_STARTED, RUNNING, PAUSED, FINISHED
}

data class TimerState(
    val status: TimerStatus = TimerStatus.NOT_STARTED,
    val isReadyCountdown: Boolean = false,
    val readyCountdownSeconds: Int = 10,
    val currentTaskIndex: Int = 0,
    val currentRound: Int = 1,
    val currentSet: Int = 1,
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val isSetRest: Boolean = false,
    val config: TimerConfig? = null,
    val roundTimes: List<Int> = emptyList() // For "For Time" mode
) {
    val currentTask: Task?
        get() = config?.tasks?.getOrNull(currentTaskIndex)

    val totalRemainingSeconds: Int
        get() {
            val cfg = config ?: return 0
            val tasksDuration = cfg.tasks.sumOf { it.durationSeconds }
            
            // Time remaining in current set
            val remainingInCurrentRound = if (isSetRest) 0 else {
                val pastTasksInRound = cfg.tasks.take(currentTaskIndex).sumOf { it.durationSeconds }
                val currentTaskPast = if (cfg.mode == TimerMode.TIMER) {
                    currentTask?.durationSeconds?.minus(remainingSeconds) ?: 0
                } else {
                    elapsedSeconds
                }
                tasksDuration - pastTasksInRound - currentTaskPast
            }
            
            val remainingRoundsInSet = cfg.rounds - currentRound
            val remainingSets = cfg.sets - currentSet
            
            val totalSeconds = if (isSetRest) {
                val restRemaining = remainingSeconds
                val remainingSetsDuration = (tasksDuration * cfg.rounds) * (cfg.sets - currentSet)
                val remainingSetRests = cfg.setRestSeconds * (cfg.sets - currentSet - 1).coerceAtLeast(0)
                restRemaining + remainingSetsDuration + remainingSetRests
            } else {
                val currentSetRemaining = remainingInCurrentRound + (remainingRoundsInSet * tasksDuration)
                val futureSetsDuration = (tasksDuration * cfg.rounds) * remainingSets
                val futureSetRests = cfg.setRestSeconds * remainingSets
                currentSetRemaining + futureSetsDuration + futureSetRests
            }
            
            return totalSeconds
        }
}

object TimerRepository {
    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    fun updateState(newState: TimerState) {
        _timerState.value = newState
    }

    fun reset() {
        _timerState.value = TimerState()
    }
}
