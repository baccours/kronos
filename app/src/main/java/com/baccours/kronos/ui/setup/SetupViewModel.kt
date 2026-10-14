package com.baccours.kronos.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.formatDuration
import com.baccours.kronos.repository.TimerPreferencesRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class SetupViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesRepository: TimerPreferencesRepository = TimerPreferencesRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimerConfig())
    val uiState: StateFlow<TimerConfig> = _uiState.asStateFlow()

    val totalDuration: StateFlow<String> = _uiState
        .map { it.totalDurationSeconds.formatDuration() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "00:00"
        )

    init {
        viewModelScope.launch {
            val savedConfig = preferencesRepository.timerConfigFlow.first()
            _uiState.value = savedConfig

            _uiState
                .debounce(300.milliseconds)
                .collect { config ->
                    preferencesRepository.saveTimerConfig(config)
                }
        }
    }

    fun updateMode(mode: TimerMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun updateSets(sets: Int) {
        _uiState.update { it.copy(sets = sets) }
    }

    fun updateSetRest(seconds: Int) {
        _uiState.update { it.copy(setRestSeconds = seconds) }
    }

    fun updateTimeCap(seconds: Int) {
        _uiState.update { it.copy(timeCap = seconds) }
    }

    fun updateTotalTimeCap(seconds: Int) {
        _uiState.update { it.copy(totalTimeCap = seconds) }
    }

    fun updateRounds(rounds: Int) {
        _uiState.update { it.copy(rounds = rounds) }
    }

    fun addTask(type: TaskType = TaskType.WORK) {
        _uiState.update { current ->
            val duration = current.tasks.findLast { it.type == type }?.durationSeconds
                ?: current.tasks.lastOrNull()?.durationSeconds
                ?: 30
            current.copy(tasks = current.tasks + Task(type = type, durationSeconds = duration))
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
