package com.baccours.kronos.repository

import com.baccours.kronos.model.TimerConfig
import kotlinx.coroutines.flow.Flow

/** Where the saved setup lives. An interface so that view models can be tested with a fake. */
interface TimerConfigStorage {
    /** Saved configuration, or defaults if missing or unreadable. */
    val timerConfigFlow: Flow<TimerConfig>

    /** Returns true on success. */
    suspend fun saveTimerConfig(config: TimerConfig): Boolean
}
