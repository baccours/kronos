package com.baccours.kronos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.repository.SettingsRepository
import com.baccours.kronos.repository.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SettingsRepository = SettingsRepository(application),
    private val store: SettingsStore = SettingsStore
) : AndroidViewModel(application) {

    /** Null until the saved settings are loaded: the UI draws nothing until then. */
    private val _settings = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.settings.first()
            store.set(saved)
            _settings.value = saved
        }
    }

    fun update(settings: AppSettings) {
        _settings.value = settings
        store.set(settings)
        viewModelScope.launch { repository.save(settings) }
    }
}
