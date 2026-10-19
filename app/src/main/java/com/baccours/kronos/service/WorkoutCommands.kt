package com.baccours.kronos.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerStateStore

/** What the UI can ask of the workout. Hides that it is a service reached with intents. */
class WorkoutCommands(
    private val context: Context,
    private val store: TimerStateStore = TimerRepository
) {

    /** False when the system refused to start the service (e.g. background start restrictions). */
    fun start(config: TimerConfig): Boolean = try {
        val intent = Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_CONFIG, TimerConfigCodec.encode(config))
        ContextCompat.startForegroundService(context, intent)
        true
    } catch (e: IllegalStateException) {
        // Includes ForegroundServiceStartNotAllowedException (Android 12+).
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

    /** Abandons a running or paused workout. */
    fun stop() = send(TimerService.ACTION_STOP)

    /**
     * Closes the results of a finished workout. The service has nothing left to do (it ends itself
     * a few seconds after finishing), so there is no need to start it just to reset the state.
     */
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
