package com.baccours.kronos.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.repository.SettingsRepository
import com.baccours.kronos.repository.SettingsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SettingsRepository = SettingsRepository(application),
    private val store: SettingsStore = SettingsStore
) : AndroidViewModel(application) {

    /** Null until the saved settings are loaded: the UI draws nothing until then. */
    val settings: StateFlow<AppSettings?> = store.settings

    fun update(settings: AppSettings) {
        store.set(settings)
        // NonCancellable: a toggle right before the screen closes must still be saved.
        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.save(settings)
            }
        }
    }
}
