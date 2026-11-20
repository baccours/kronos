package com.baccours.kronos.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import kotlinx.coroutines.flow.Flow

class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsStorage {

    constructor(context: Context) : this(context.kronosDataStore)

    private object Keys {
        val BEEPS = booleanPreferencesKey("beeps")
        val VOICE = booleanPreferencesKey("voice")
        val THEME = stringPreferencesKey("theme")
    }

    override val settings: Flow<AppSettings> = dataStore.readSafely(TAG, AppSettings()) { prefs ->
        val defaults = AppSettings()
        AppSettings(
            beeps = prefs[Keys.BEEPS] ?: defaults.beeps,
            voice = prefs[Keys.VOICE] ?: defaults.voice,
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: defaults.theme
        )
    }

    override suspend fun save(settings: AppSettings): Boolean =
        dataStore.editSafely(TAG) { prefs ->
            prefs[Keys.BEEPS] = settings.beeps
            prefs[Keys.VOICE] = settings.voice
            prefs[Keys.THEME] = settings.theme.name
        }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
