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
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The app's single preferences file, shared by the settings and the saved setup. */
internal val Context.kronosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kronos_prefs",
    // A corrupt file would otherwise fail every later save.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Reads [transform] of the preferences; [default] if the file cannot be read or decoded. */
internal fun <T> DataStore<Preferences>.readSafely(
    tag: String,
    default: T,
    transform: (Preferences) -> T
): Flow<T> = data.map(transform).catch { error ->
    if (error !is Exception) throw error
    Log.w(tag, "Cannot read preferences", error)
    emit(default)
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
