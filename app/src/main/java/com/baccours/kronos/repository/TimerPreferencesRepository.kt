package com.baccours.kronos.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baccours.kronos.model.TimerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kronos_timer_prefs")

class TimerPreferencesRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object PreferencesKeys {
        val TIMER_CONFIG = stringPreferencesKey("timer_config_json")
    }

    val timerConfigFlow: Flow<TimerConfig> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.TIMER_CONFIG]
            if (jsonString != null) {
                try {
                    json.decodeFromString<TimerConfig>(jsonString)
                } catch (_: Exception) {
                    TimerConfig()
                }
            } else {
                TimerConfig()
            }
        }

    suspend fun saveTimerConfig(config: TimerConfig) {
        val jsonString = json.encodeToString(TimerConfig.serializer(), config)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIMER_CONFIG] = jsonString
        }
    }
}
