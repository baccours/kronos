package com.baccours.kronos.service

import android.content.Context
import android.util.Log
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore

/** How commands reach [TimerService]; an interface so that the refusal paths can be tested. */
internal interface CommandTransport {
    /** Starts the service in the foreground with the encoded workout. Throws if the system refuses. */
    fun startWorkout(encodedConfig: String)

    /** Sends [action] to the service. Throws [IllegalStateException] if the system refuses. */
    fun send(action: String)

    fun stopService()

    fun cancelNotification()
}

/** Helper class sending workout command intents to [TimerService]. */
class WorkoutCommands internal constructor(
    private val transport: CommandTransport,
    private val store: TimerStateStore
) {

    constructor(context: Context, store: TimerStateStore) :
        this(AndroidCommandTransport(context.applicationContext), store)

    /** Returns false if system prevents starting foreground service. */
    fun start(config: TimerConfig): Boolean = try {
        transport.startWorkout(TimerConfigCodec.encode(config))
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
        if (finished) transport.stopService()
        if (finished || !send(TimerService.ACTION_STOP)) {
            store.reset()
            transport.cancelNotification()
        }
    }

    /** Returns false if the system refuses the command. */
    private fun send(action: String): Boolean = try {
        transport.send(action)
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Cannot send $action to the workout service", e)
        false
    }

    private companion object {
        const val TAG = "WorkoutCommands"
    }
}
