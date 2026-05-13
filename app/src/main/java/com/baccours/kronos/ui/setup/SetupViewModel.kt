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

    fun updateSetRest(seconds: Int) {
        _uiState.update { it.copy(setRestSeconds = seconds) }
    }

    fun updateRounds(rounds: Int) {
        _uiState.update { it.copy(rounds = rounds) }
    }

    fun addTask() {
        _uiState.update {
            val lastType = it.tasks.lastOrNull()?.type ?: TaskType.REST
            val nextType = if (lastType == TaskType.WORK) TaskType.REST else TaskType.WORK
            it.copy(tasks = it.tasks + Task(type = nextType))
        }
    }

    fun removeTask(id: String) {
        _uiState.update {
            it.copy(tasks = it.tasks.filter { task -> task.id != id })
        }
    }

    fun updateTaskDuration(id: String, durationSeconds: Int) {
        _uiState.update {
            it.copy(tasks = it.tasks.map { task ->
                if (task.id == id) task.copy(durationSeconds = durationSeconds) else task
            })
        }
    }

}
