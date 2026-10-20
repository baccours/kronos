package com.baccours.kronos.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerStateStore

/** Helper class sending workout command intents to [TimerService]. */
class WorkoutCommands(
    private val context: Context,
    private val store: TimerStateStore = TimerRepository
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

    fun pause() = send(TimerService.ACTION_PAUSE)
    fun resume() = send(TimerService.ACTION_RESUME)
    fun skip() = send(TimerService.ACTION_SKIP)
    fun lap() = send(TimerService.ACTION_LAP)

    /** Stops the active workout. */
    fun stop() = send(TimerService.ACTION_STOP)

    /** Resets state and dismisses notification for finished workouts. */
    fun dismissFinished() {
        store.reset()
        TimerNotifier(context).cancel()
    }

    private fun send(action: String) {
        try {
            context.startService(Intent(context, TimerService::class.java).setAction(action))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot send $action to the workout service", e)
        }
    }

    private companion object {
        const val TAG = "WorkoutCommands"
    }
}
