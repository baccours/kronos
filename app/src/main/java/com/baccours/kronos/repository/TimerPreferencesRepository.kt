package com.baccours.kronos.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kronos_timer_prefs",
    // A corrupt file would otherwise fail every later save.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class TimerPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val TIMER_CONFIG = stringPreferencesKey("timer_config_json")
    }

    /** Flow emitting saved configuration or defaults on error/missing data. */
    val timerConfigFlow: Flow<TimerConfig> = context.dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Cannot read the saved setup", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            TimerConfigCodec.decode(preferences[PreferencesKeys.TIMER_CONFIG]) ?: TimerConfig()
        }

    /** Saves config to DataStore. Returns true on success. */
    suspend fun saveTimerConfig(config: TimerConfig): Boolean = try {
        val jsonString = TimerConfigCodec.encode(config)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIMER_CONFIG] = jsonString
        }
        true
    } catch (e: IOException) {
        Log.w(TAG, "Cannot save the setup", e)
        false
    }

    private companion object {
        const val TAG = "TimerPreferences"
    }
}
