package com.baccours.kronos.ui.setup

import androidx.lifecycle.ViewModel
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SetupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TimerConfig())
    val uiState: StateFlow<TimerConfig> = _uiState.asStateFlow()

    fun updateMode(mode: TimerMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun updateSets(sets: Int) {
        _uiState.update { it.copy(sets = sets) }
    }

    fun updateRoundsPerSet(rounds: Int) {
        _uiState.update { it.copy(roundsPerSet = rounds) }
    }

    fun addItem() {
        _uiState.update {
            val lastType = it.items.lastOrNull()?.type ?: TaskType.REST
            val nextType = if (lastType == TaskType.WORK) TaskType.REST else TaskType.WORK
            it.copy(items = it.items + Task(type = nextType))
        }
    }

    fun removeItem(id: String) {
        _uiState.update {
            it.copy(items = it.items.filter { item -> item.id != id })
        }
    }

    fun updateItemDuration(id: String, durationSeconds: Int) {
        _uiState.update {
            it.copy(items = it.items.map { item ->
                if (item.id == id) item.copy(durationSeconds = durationSeconds) else item
            })
        }
    }

    fun updateRestBetweenSets(durationSeconds: Int) {
        _uiState.update { it.copy(restBetweenSetsSeconds = durationSeconds) }
    }
}
