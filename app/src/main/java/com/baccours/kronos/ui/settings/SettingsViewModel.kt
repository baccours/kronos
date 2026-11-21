package com.baccours.kronos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.repository.SettingsStorage
import com.baccours.kronos.repository.SettingsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsStorage,
    private val store: SettingsStore
) : ViewModel() {

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
