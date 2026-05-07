package com.baccours.kronos.ui.setup

import androidx.lifecycle.ViewModel
import com.baccours.kronos.model.WorkoutConfig
import com.baccours.kronos.model.WorkoutItem
import com.baccours.kronos.model.WorkoutItemType
import com.baccours.kronos.model.WorkoutMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SetupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutConfig())
    val uiState: StateFlow<WorkoutConfig> = _uiState.asStateFlow()

    fun updateMode(mode: WorkoutMode) {
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
            val lastType = it.items.lastOrNull()?.type ?: WorkoutItemType.REST
            val nextType = if (lastType == WorkoutItemType.WORK) WorkoutItemType.REST else WorkoutItemType.WORK
            it.copy(items = it.items + WorkoutItem(type = nextType))
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
