package com.baccours.kronos.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import kotlinx.coroutines.flow.Flow

class TimerPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) : TimerConfigStorage {

    constructor(context: Context) : this(context.kronosDataStore)

    private object PreferencesKeys {
        val TIMER_CONFIG = stringPreferencesKey("timer_config_json")
    }

    override val timerConfigFlow: Flow<TimerConfig> =
        dataStore.readSafely(TAG, TimerConfig()) { preferences ->
            TimerConfigCodec.decode(preferences[PreferencesKeys.TIMER_CONFIG]) ?: TimerConfig()
        }

    /** Saves config to DataStore. Returns true on success. */
    override suspend fun saveTimerConfig(config: TimerConfig): Boolean {
        val jsonString = TimerConfigCodec.encode(config)
        return dataStore.editSafely(TAG) { it[PreferencesKeys.TIMER_CONFIG] = jsonString }
    }

    private companion object {
        const val TAG = "TimerPreferences"
    }
}
