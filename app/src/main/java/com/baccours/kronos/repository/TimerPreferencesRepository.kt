package com.baccours.kronos.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import kotlinx.coroutines.flow.Flow

class TimerPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val TIMER_CONFIG = stringPreferencesKey("timer_config_json")
    }

    /** Saved configuration, or defaults if missing or unreadable. */
    val timerConfigFlow: Flow<TimerConfig> =
        context.kronosDataStore.readSafely(TAG, TimerConfig()) { preferences ->
            TimerConfigCodec.decode(preferences[PreferencesKeys.TIMER_CONFIG]) ?: TimerConfig()
        }

    /** Saves config to DataStore. Returns true on success. */
    suspend fun saveTimerConfig(config: TimerConfig): Boolean {
        val jsonString = TimerConfigCodec.encode(config)
        return context.kronosDataStore.editSafely(TAG) { it[PreferencesKeys.TIMER_CONFIG] = jsonString }
    }

    private companion object {
        const val TAG = "TimerPreferences"
    }
}
