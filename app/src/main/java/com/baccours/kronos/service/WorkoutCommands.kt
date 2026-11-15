package com.baccours.kronos.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore

/** Helper class sending workout command intents to [TimerService]. */
class WorkoutCommands(
    private val context: Context,
    private val store: TimerStateStore = TimerStateStore
) {

    /** Returns false if system prevents starting foreground service. */
    fun start(config: TimerConfig): Boolean = try {
        val intent = Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_CONFIG, TimerConfigCodec.encode(config))
        ContextCompat.startForegroundService(context, intent)
        true
    } catch (e: IllegalStateException) {
        // Handles foreground service launch restrictions
        Log.e(TAG, "Cannot start the workout service", e)
        false
    } catch (e: SecurityException) {
        Log.e(TAG, "Not allowed to start the workout service", e)
        false
    }

    fun pause() { send(TimerService.ACTION_PAUSE) }
    fun resume() { send(TimerService.ACTION_RESUME) }
    fun skip() { send(TimerService.ACTION_SKIP) }
    fun lap() { send(TimerService.ACTION_LAP) }

    /**
     * Stops the workout. The service resets the state and the notification itself, so a tick
     * still queued cannot repaint the screen afterward; without a service to do it, we do.
     */
    fun stop() {
        val finished = store.timerState.value.status == TimerStatus.FINISHED
        // A finished workout has nothing left to stop, but a lingering service may remain.
        if (finished) context.stopService(Intent(context, TimerService::class.java))
        if (finished || !send(TimerService.ACTION_STOP)) {
            store.reset()
            NotificationManagerCompat.from(context).cancel(TimerNotifier.NOTIFICATION_ID)
        }
    }

    /** Returns false if the system refuses the command. */
    private fun send(action: String): Boolean = try {
        context.startService(Intent(context, TimerService::class.java).setAction(action))
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Cannot send $action to the workout service", e)
        false
    }

    private companion object {
        const val TAG = "WorkoutCommands"
    }
}
