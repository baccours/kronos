package com.baccours.kronos.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BEEPS = booleanPreferencesKey("beeps")
        val VOICE = booleanPreferencesKey("voice")
        val THEME = stringPreferencesKey("theme")
    }

    /** Saved settings; defaults for anything missing, unreadable or unknown. */
    val settings: Flow<AppSettings> = context.kronosDataStore.readSafely(TAG)
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                beeps = prefs[Keys.BEEPS] ?: defaults.beeps,
                voice = prefs[Keys.VOICE] ?: defaults.voice,
                theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: defaults.theme
            )
        }

    /** Returns true on success. */
    suspend fun save(settings: AppSettings): Boolean =
        context.kronosDataStore.editSafely(TAG) { prefs ->
            prefs[Keys.BEEPS] = settings.beeps
            prefs[Keys.VOICE] = settings.voice
            prefs[Keys.THEME] = settings.theme.name
        }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
