package com.baccours.kronos.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/** The app's single preferences file, shared by the settings and the saved setup. */
internal val Context.kronosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kronos_prefs",
    // A corrupt file would otherwise fail every later save.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Preferences, or empty ones if the file cannot be read. */
internal fun DataStore<Preferences>.readSafely(tag: String): Flow<Preferences> =
    data.catch { error ->
        if (error is IOException) {
            Log.w(tag, "Cannot read preferences", error)
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

/** Returns true on success. */
internal suspend fun DataStore<Preferences>.editSafely(
    tag: String,
    transform: (MutablePreferences) -> Unit
): Boolean = try {
    edit { transform(it) }
    true
} catch (e: IOException) {
    Log.w(tag, "Cannot save preferences", e)
    false
}
