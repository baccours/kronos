package com.baccours.kronos.repository

import com.baccours.kronos.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide copy of the settings, readable synchronously by the service (like [TimerStateStore]).
 * Kept up to date by the UI, which loads and saves the settings.
 */
object SettingsStore {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun set(settings: AppSettings) {
        _settings.value = settings
    }
}
