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
    val currentItemIndex: Int = 0,
    val currentRound: Int = 1,
    val currentSet: Int = 1,
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val isRestBetweenSets: Boolean = false,
    val config: TimerConfig? = null,
    val roundTimes: List<Int> = emptyList() // For "For Time" mode
) {
    val currentItem: Task?
        get() = config?.items?.getOrNull(currentItemIndex)

    val totalRemainingSeconds: Int
        get() {
            val cfg = config ?: return 0
            val itemsDuration = cfg.items.sumOf { it.durationSeconds }
            
            // Time remaining in current set
            val remainingInCurrentRound = if (isRestBetweenSets) 0 else {
                val pastItemsInRound = cfg.items.take(currentItemIndex).sumOf { it.durationSeconds }
                val currentItemPast = if (cfg.mode == TimerMode.TIMER) {
                    currentItem?.durationSeconds?.minus(remainingSeconds) ?: 0
                } else {
                    elapsedSeconds
                }
                itemsDuration - pastItemsInRound - currentItemPast
            }
            
            val remainingRoundsInSet = cfg.roundsPerSet - currentRound
            val remainingSets = cfg.sets - currentSet
            
            val totalSeconds = if (isRestBetweenSets) {
                val restRemaining = remainingSeconds
                val remainingSetsDuration = (itemsDuration * cfg.roundsPerSet) * (cfg.sets - currentSet)
                val remainingSetRests = cfg.restBetweenSetsSeconds * (cfg.sets - currentSet - 1).coerceAtLeast(0)
                restRemaining + remainingSetsDuration + remainingSetRests
            } else {
                val currentSetRemaining = remainingInCurrentRound + (remainingRoundsInSet * itemsDuration)
                val futureSetsDuration = (itemsDuration * cfg.roundsPerSet) * remainingSets
                val futureSetRests = cfg.restBetweenSetsSeconds * remainingSets
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
