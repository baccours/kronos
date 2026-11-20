package com.baccours.kronos.service

import android.app.AlarmManager
import android.content.Context
import android.os.Handler
import android.os.SystemClock

/**
 * Ends a forgotten paused workout. An alarm, unlike a handler delay,
 * still counts time while the device sleeps. [onTimeout] runs on [handler].
 */
internal class PauseTimeout(
    context: Context,
    private val handler: Handler,
    onTimeout: () -> Unit,
    /** Only tests use another value than the default. */
    private val timeoutMs: Long = TIMEOUT_MS
) : PauseAlarm {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val listener = AlarmManager.OnAlarmListener { onTimeout() }

    override fun start() {
        alarms.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + timeoutMs,
            "kronos:pause-timeout",
            listener,
            handler
        )
    }

    override fun cancel() = alarms.cancel(listener)

    internal companion object {
        const val TIMEOUT_MS = 60L * 60 * 1000
    }
}
