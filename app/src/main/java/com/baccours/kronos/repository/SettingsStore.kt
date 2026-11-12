package com.baccours.kronos.repository

import com.baccours.kronos.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide settings: the single source of truth for the UI and the service
 * (like [TimerStateStore]). Loaded once by [com.baccours.kronos.KronosApp].
 */
object SettingsStore {
    private val _settings = MutableStateFlow<AppSettings?>(null)

    /** Null until the saved settings are loaded. */
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    /** For synchronous readers: defaults until loaded. */
    val current: AppSettings get() = _settings.value ?: AppSettings()

    /** Sets the saved settings, unless the user already changed them. */
    fun load(saved: AppSettings) {
        _settings.compareAndSet(null, saved)
    }

    fun set(settings: AppSettings) {
        _settings.value = settings
    }
}
