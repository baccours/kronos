package com.baccours.kronos

import android.content.Context
import com.baccours.kronos.repository.SettingsRepository
import com.baccours.kronos.repository.SettingsStore
import com.baccours.kronos.repository.TimerStateStore

/** The objects the activity and the service share. One per process, created by [KronosApp]. */
class AppContainer(context: Context) {
    val timerStateStore = TimerStateStore()
    val settingsStore = SettingsStore()
    val settingsRepository = SettingsRepository(context)
}
