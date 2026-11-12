package com.baccours.kronos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.repository.SettingsRepository
import com.baccours.kronos.repository.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SettingsRepository = SettingsRepository(application),
    private val store: SettingsStore = SettingsStore
) : AndroidViewModel(application) {

    /** Null until the saved settings are loaded: the UI draws nothing until then. */
    val settings: StateFlow<AppSettings?> = store.settings

    fun update(settings: AppSettings) {
        store.set(settings)
        viewModelScope.launch { repository.save(settings) }
    }
}
