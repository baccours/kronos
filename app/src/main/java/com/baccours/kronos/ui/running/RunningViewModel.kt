package com.baccours.kronos.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.formatDuration
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerState
import com.baccours.kronos.repository.TimerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RunningUiState(
    val status: TimerStatus = TimerStatus.NOT_STARTED,
    val currentSetText: String = "",
    val currentRoundText: String = "",
    val taskName: String = "",
    val isWorkTask: Boolean = true,
    val displayTime: String = "00:00",
    val totalRemainingTime: String? = null,
    val isReadyCountdown: Boolean = false,
    val isSetRest: Boolean = false,
    val mode: TimerMode? = null,
    val roundTimes: List<String> = emptyList(),
    val nextButtonText: String? = null
)

class RunningViewModel : ViewModel() {

    val uiState: StateFlow<RunningUiState> = TimerRepository.timerState
        .map { state ->
            val config = state.config
            val workTasks = config?.tasks?.filter { it.type == TaskType.WORK } ?: emptyList()
            val workTasksCount = workTasks.size.coerceAtLeast(1)

            RunningUiState(
                status = state.status,
                currentSetText = "${state.currentSet}/${config?.sets ?: 1}",
                currentRoundText = "${state.currentRound}/${config?.rounds ?: 1}",
                taskName = getTaskName(state),
                isWorkTask = isWorkTask(state),
                displayTime = getDisplaySeconds(state).formatDuration(),
                totalRemainingTime = if (config?.mode == TimerMode.TIMER && !state.isReadyCountdown) {
                    state.totalRemainingSeconds.formatDuration()
                } else null,
                isReadyCountdown = state.isReadyCountdown,
                isSetRest = state.isSetRest,
                mode = config?.mode,
                roundTimes = state.roundTimes.mapIndexed { index, seconds ->
                    val roundNum = (index / workTasksCount) + 1
                    val workTaskIndex = index % workTasksCount
                    val workTask = workTasks.getOrNull(workTaskIndex)
                    val label = workTask?.name?.ifBlank {
                        if (workTasks.size > 1) "Task ${workTaskIndex + 1}" else "Round $roundNum"
                    } ?: "Task"

                    if (workTasks.size > 1) {
                        "R$roundNum $label: ${seconds.formatDuration()}"
                    } else {
                        "$label: ${seconds.formatDuration()}"
                    }
                },
                nextButtonText = getNextButtonText(state)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RunningUiState()
        )

    private fun getTaskName(state: TimerState): String {
        return when {
            state.isReadyCountdown -> "GET READY"
            state.isSetRest -> "REST BETWEEN SETS"
            else -> state.currentTask?.let { task ->
                task.name.ifBlank { task.type.name }
            } ?: "WORK"
        }
    }

    private fun getDisplaySeconds(state: TimerState): Int {
        val config = state.config ?: return 0
        return if (config.mode == TimerMode.TIMER || state.isSetRest || state.isReadyCountdown) {
            state.remainingSeconds
        } else {
            state.elapsedSeconds
        }
    }

    private fun isWorkTask(state: TimerState): Boolean {
        return !(state.currentTask?.type == TaskType.REST || state.isSetRest || state.isReadyCountdown)
    }

    private fun getNextButtonText(state: TimerState): String? {
        val config = state.config ?: return null
        if (config.mode != TimerMode.FOR_TIME || state.isReadyCountdown || state.isSetRest || state.currentTask?.type == TaskType.REST) return null

        return if (state.currentTaskIndex == config.tasks.size - 1) {
            if (state.currentRound == config.rounds) {
                if (state.currentSet == config.sets) "FINISH" else "NEXT SET"
            } else "NEXT ROUND"
        } else {
            "NEXT EXERCISE"
        }
    }
}
