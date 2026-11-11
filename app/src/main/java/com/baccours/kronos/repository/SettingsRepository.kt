package com.baccours.kronos.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kronos_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BEEPS = booleanPreferencesKey("beeps")
        val VOICE = booleanPreferencesKey("voice")
        val THEME = stringPreferencesKey("theme")
    }

    /** Saved settings; defaults for anything missing, unreadable or unknown. */
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Cannot read the settings", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                beeps = prefs[Keys.BEEPS] ?: defaults.beeps,
                voice = prefs[Keys.VOICE] ?: defaults.voice,
                theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: defaults.theme
            )
        }

    /** Returns true on success. */
    suspend fun save(settings: AppSettings): Boolean = try {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.BEEPS] = settings.beeps
            prefs[Keys.VOICE] = settings.voice
            prefs[Keys.THEME] = settings.theme.name
        }
        true
    } catch (e: IOException) {
        Log.w(TAG, "Cannot save the settings", e)
        false
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
