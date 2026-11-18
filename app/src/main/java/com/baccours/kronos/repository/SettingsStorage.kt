package com.baccours.kronos.repository

import com.baccours.kronos.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Where the settings live. An interface so that view models can be tested with a fake. */
interface SettingsStorage {
    /** Saved settings; defaults for anything missing, unreadable or unknown. */
    val settings: Flow<AppSettings>

    /** Returns true on success. */
    suspend fun save(settings: AppSettings): Boolean
}
