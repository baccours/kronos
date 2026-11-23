package com.baccours.kronos

import android.content.Context
import com.baccours.kronos.repository.SettingsRepository
import com.baccours.kronos.repository.TimerConfigRepository
import com.baccours.kronos.repository.WorkoutStateStore
import kotlinx.coroutines.CoroutineScope

/** The objects the activity and the service share. One per process, created by [KronosApp]. */
class AppContainer(context: Context, scope: CoroutineScope) {
    val workoutStateStore = WorkoutStateStore()
    val settingsRepository = SettingsRepository(context, scope)
    val timerConfigRepository = TimerConfigRepository(context)
}
