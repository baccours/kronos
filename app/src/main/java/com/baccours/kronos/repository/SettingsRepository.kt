package com.baccours.kronos.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The settings: the single source of truth for the UI and the service, saved with DataStore.
 * One instance per process (see [com.baccours.kronos.AppContainer]); it loads itself on creation
 * and saves every change from [scope], so a toggle made just before the screen closes is not lost.
 */
class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) {

    constructor(context: Context, scope: CoroutineScope) : this(context.kronosDataStore, scope)

    private object Keys {
        val BEEPS = booleanPreferencesKey("beeps")
        val VOICE = booleanPreferencesKey("voice")
        val THEME = stringPreferencesKey("theme")
    }

    private val _settings = MutableStateFlow<AppSettings?>(null)

    /** Null until the saved settings are loaded. */
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    /** For synchronous readers: defaults until loaded. */
    val current: AppSettings get() = _settings.value ?: AppSettings()

    init {
        scope.launch {
            var saved = dataStore.readSafely(TAG, AppSettings(), ::read).first()
            // A change made before the disk read finished wins.
            _settings.compareAndSet(null, saved)
            // The flow is conflated: the latest value is saved, in order. A failed save is retried
            // with the next change.
            _settings.filterNotNull().collect { latest ->
                if (latest != saved && dataStore.editSafely(TAG) { write(it, latest) }) saved = latest
            }
        }
    }

    /** Applies [transform] to the current settings atomically, so quick successive changes all count. */
    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update { transform(it ?: AppSettings()) }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            beeps = prefs[Keys.BEEPS] ?: defaults.beeps,
            voice = prefs[Keys.VOICE] ?: defaults.voice,
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: defaults.theme
        )
    }

    private fun write(prefs: MutablePreferences, settings: AppSettings) {
        prefs[Keys.BEEPS] = settings.beeps
        prefs[Keys.VOICE] = settings.voice
        prefs[Keys.THEME] = settings.theme.name
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
