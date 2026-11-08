package com.baccours.kronos.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.repository.TimerPreferencesRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class SetupViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesRepository: TimerPreferencesRepository = TimerPreferencesRepository(application)
) : AndroidViewModel(application) {

    /** Null until the saved setup is loaded: the UI draws nothing and edits are ignored. */
    private val _uiState = MutableStateFlow<TimerConfig?>(null)
    val uiState: StateFlow<TimerConfig?> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = preferencesRepository.timerConfigFlow.first()
            _uiState.value = saved

            // Persist configuration changes when modified
            var lastSaved = saved
            _uiState
                .filterNotNull()
                .debounce(300.milliseconds)
                .collect { config ->
                    if (config != lastSaved && preferencesRepository.saveTimerConfig(config)) {
                        lastSaved = config
                    }
                }
        }
    }

    private fun edit(transform: (TimerConfig) -> TimerConfig) {
        _uiState.update { it?.let(transform) }
    }

    fun updateMode(mode: TimerMode) = edit { it.copy(mode = mode) }

    fun updateSets(sets: Int) = edit { it.copy(sets = sets.coerceIn(1, TimerConfig.MAX_SETS)) }

    fun updateSetRest(seconds: Int) =
        edit { it.copy(setRestSeconds = seconds.coerceIn(0, TimerConfig.MAX_DURATION_SECONDS)) }

    fun updateTimeCap(seconds: Int) =
        edit { it.copy(timeCap = seconds.coerceIn(0, TimerConfig.MAX_DURATION_SECONDS)) }

    fun updateTotalTimeCap(seconds: Int) =
        edit { it.copy(totalTimeCap = seconds.coerceIn(0, TimerConfig.MAX_DURATION_SECONDS)) }

    fun updateRounds(rounds: Int) = edit { it.copy(rounds = rounds.coerceIn(1, TimerConfig.MAX_ROUNDS)) }

    fun addTask(type: TaskType = TaskType.WORK) = edit { current ->
        if (current.tasks.size >= TimerConfig.MAX_TASKS) {
            current
        } else {
            val duration = current.tasks.findLast { it.type == type }?.durationSeconds
                ?: current.tasks.lastOrNull()?.durationSeconds
                ?: 30
            current.copy(tasks = current.tasks + Task(type = type, durationSeconds = duration))
        }
    }

    fun removeTask(id: String) = edit {
        it.copy(tasks = it.tasks.filter { task -> task.id != id })
    }

    fun updateTaskDuration(id: String, durationSeconds: Int) = edit {
        it.copy(tasks = it.tasks.map { task ->
            if (task.id == id) {
                task.copy(durationSeconds = durationSeconds.coerceIn(0, TimerConfig.MAX_DURATION_SECONDS))
            } else task
        })
    }
}
