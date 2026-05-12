package com.baccours.kronos.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerMode
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
    val itemName: String = "",
    val isWorkItem: Boolean = true,
    val displayTime: String = "00:00",
    val totalRemainingTime: String? = null,
    val isReadyCountdown: Boolean = false,
    val isRestBetweenSets: Boolean = false,
    val mode: TimerMode? = null,
    val roundTimes: List<String> = emptyList(),
    val nextButtonText: String? = null
)

class RunningViewModel : ViewModel() {

    val uiState: StateFlow<RunningUiState> = TimerRepository.timerState
        .map { state ->
            val config = state.config
            val workItems = config?.items?.filter { it.type == TaskType.WORK } ?: emptyList()
            val workItemsCount = workItems.size.coerceAtLeast(1)

            RunningUiState(
                status = state.status,
                currentSetText = "${state.currentSet}/${config?.sets ?: 1}",
                currentRoundText = "${state.currentRound}/${config?.roundsPerSet ?: 1}",
                itemName = getItemName(state),
                isWorkItem = isWorkItem(state),
                displayTime = formatTime(getDisplaySeconds(state)),
                totalRemainingTime = if (config?.mode == TimerMode.TIMER && !state.isReadyCountdown) {
                    formatTime(state.totalRemainingSeconds)
                } else null,
                isReadyCountdown = state.isReadyCountdown,
                isRestBetweenSets = state.isRestBetweenSets,
                mode = config?.mode,
                roundTimes = state.roundTimes.mapIndexed { index, seconds ->
                    val roundNum = (index / workItemsCount) + 1
                    val workItemIndex = index % workItemsCount
                    val workItem = workItems.getOrNull(workItemIndex)
                    val label = workItem?.name?.ifBlank {
                        if (workItems.size > 1) "Item ${workItemIndex + 1}" else "Round $roundNum"
                    } ?: "Item"

                    if (workItems.size > 1) {
                        "R$roundNum $label: ${formatTime(seconds)}"
                    } else {
                        "$label: ${formatTime(seconds)}"
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

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    private fun getItemName(state: TimerState): String {
        return when {
            state.isReadyCountdown -> "GET READY"
            state.isRestBetweenSets -> "REST BETWEEN SETS"
            else -> state.currentItem?.let { item ->
                item.name.ifBlank { item.type.name }
            } ?: "WORK"
        }
    }

    private fun getDisplaySeconds(state: TimerState): Int {
        val config = state.config ?: return 0
        return if (config.mode == TimerMode.TIMER || state.isRestBetweenSets || state.isReadyCountdown) {
            state.remainingSeconds
        } else {
            state.elapsedSeconds
        }
    }

    private fun isWorkItem(state: TimerState): Boolean {
        return !(state.currentItem?.type == TaskType.REST || state.isRestBetweenSets || state.isReadyCountdown)
    }

    private fun getNextButtonText(state: TimerState): String? {
        val config = state.config ?: return null
        if (config.mode != TimerMode.FOR_TIME || state.isReadyCountdown || state.isRestBetweenSets || state.currentItem?.type == TaskType.REST) return null

        return if (state.currentItemIndex == config.items.size - 1) {
            if (state.currentRound == config.roundsPerSet) {
                if (state.currentSet == config.sets) "FINISH" else "NEXT SET"
            } else "NEXT ROUND"
        } else {
            "NEXT EXERCISE"
        }
    }
}
