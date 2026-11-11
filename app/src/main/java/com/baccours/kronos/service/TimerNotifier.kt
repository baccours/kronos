package com.baccours.kronos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.baccours.kronos.MainActivity
import com.baccours.kronos.R
import com.baccours.kronos.formatDuration
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.phaseLabel

/**
 * Manages workout notifications using system chronometer rendering.
 */
class TimerNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Notification state key for change detection. */
    private data class Key(val status: TimerStatus, val segmentIndex: Int, val lapCount: Int)

    private var lastKey: Key? = null

    private fun keyOf(state: TimerState) = Key(state.status, state.segmentIndex, state.laps.size)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Builds initial foreground service notification. */
    fun buildForForeground(state: TimerState): Notification {
        lastKey = null
        return build(state)
    }

    /** Posts notification for [state]. */
    fun show(state: TimerState) {
        if (state.status == TimerStatus.NOT_STARTED) return
        lastKey = keyOf(state)
        manager.notify(NOTIFICATION_ID, build(state))
    }

    /** Updates notification if non-chronometer state changed. */
    fun updateIfChanged(state: TimerState) {
        if (keyOf(state) != lastKey) show(state)
    }

    fun cancel() {
        lastKey = null
        manager.cancel(NOTIFICATION_ID)
    }

    private fun build(state: TimerState): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cfg = state.config
        val isForTime = cfg?.mode == TimerMode.FOR_TIME
        val finished = state.status == TimerStatus.FINISHED
        val paused = state.status == TimerStatus.PAUSED
        val countsDown = state.segmentSeconds > 0 && !(isForTime && state.phase == Phase.WORK)

        val title = if (finished) {
            context.getString(R.string.notification_finished_title)
        } else {
            context.phaseLabel(state.phase, state.workIndex)
        }

        val sets = cfg?.sets ?: 1
        val rounds = cfg?.rounds ?: 1
        val progress = if (isForTime) {
            context.getString(R.string.notification_progress_for_time, state.currentSet, sets, state.currentRound)
        } else {
            context.getString(R.string.notification_progress_timer, state.currentSet, sets, state.currentRound, rounds)
        }
        val shownSeconds = if (countsDown) state.remainingSeconds else state.elapsedSeconds
        val text = when {
            finished -> context.getString(R.string.notification_finished_text)
            paused -> context.getString(R.string.notification_paused, shownSeconds.formatDuration())
            else -> progress
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openApp)
            .setOngoing(!finished)
            .setAutoCancel(finished)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.status == TimerStatus.RUNNING) {
            // System chronometer handles per-second visual updates
            val nowWall = System.currentTimeMillis()
            val whenMs = if (countsDown) {
                nowWall + state.segmentSeconds * 1000L - state.segmentElapsedMs
            } else {
                nowWall - state.segmentElapsedMs
            }
            builder.setShowWhen(true)
                .setWhen(whenMs)
                .setUsesChronometer(true)
                .setChronometerCountDown(countsDown)
        } else {
            builder.setShowWhen(false)
        }

        when (state.status) {
            TimerStatus.RUNNING -> {
                if (isForTime && state.phase == Phase.WORK) {
                    builder.addAction(0, context.getString(R.string.action_round), serviceAction(TimerService.ACTION_LAP))
                }
                builder.addAction(0, context.getString(R.string.action_pause), serviceAction(TimerService.ACTION_PAUSE))
            }
            TimerStatus.PAUSED ->
                builder.addAction(0, context.getString(R.string.action_resume), serviceAction(TimerService.ACTION_RESUME))
            else -> Unit
        }
        if (!finished) {
            builder.addAction(0, context.getString(R.string.action_stop), serviceAction(TimerService.ACTION_STOP))
        }
        return builder.build()
    }

    private fun serviceAction(action: String): PendingIntent {
        val intent = Intent(context, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "timer_channel"
    }
}
