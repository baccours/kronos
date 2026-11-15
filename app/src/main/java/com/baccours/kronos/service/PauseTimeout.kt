package com.baccours.kronos.service

import android.app.AlarmManager
import android.content.Context
import android.os.Handler
import android.os.SystemClock

/**
 * Ends a forgotten paused workout. An alarm, unlike a handler delay,
 * still counts time while the device sleeps. [onTimeout] runs on [handler].
 */
class PauseTimeout(context: Context, private val handler: Handler, onTimeout: () -> Unit) {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val listener = AlarmManager.OnAlarmListener { onTimeout() }

    fun start() {
        alarms.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + TIMEOUT_MS,
            "kronos:pause-timeout",
            listener,
            handler
        )
    }

    fun cancel() = alarms.cancel(listener)

    private companion object {
        const val TIMEOUT_MS = 60L * 60 * 1000
    }
}
