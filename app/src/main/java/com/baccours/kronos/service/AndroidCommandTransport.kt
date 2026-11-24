package com.baccours.kronos.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** [CommandTransport] over the real service intents. */
internal class AndroidCommandTransport(private val context: Context) : CommandTransport {

    override fun startWorkout(encodedConfig: String) {
        val intent = Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_CONFIG, encodedConfig)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun send(action: String) {
        context.startService(Intent(context, TimerService::class.java).setAction(action))
    }

    override fun stopService() {
        context.stopService(Intent(context, TimerService::class.java))
    }

    override fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(TimerNotifier.NOTIFICATION_ID)
    }
}
