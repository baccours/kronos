package com.baccours.kronos.service

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.TickScheduler
import com.baccours.kronos.engine.WorkoutController
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore

/** What the session needs from the Android service it runs in. */
internal interface SessionHost {
    /** Promotes the service to the foreground, showing [state]. False if the system refuses. */
    fun enterForeground(state: TimerState): Boolean

    /** Leaves the foreground; the notification is removed, or kept (detached) if [removeNotification] is false. */
    fun leaveForeground(removeNotification: Boolean)

    fun stopSelf()

    fun logWarning(message: String)

    fun logError(message: String, error: Throwable)
}

/** The workout notification, as the session drives it. */
internal interface SessionNotifier {
    fun show(state: TimerState)

    /** Re-posts only if something other than the chronometer changed. */
    fun updateIfChanged(state: TimerState)

    fun cancel()

    fun showPauseTimeout()
}

/** The CPU wake lock held while a workout runs. */
internal interface HeldLock {
    fun acquire()

    /** Extends the lock if it is held and getting old; does nothing once release. */
    fun renewIfDue()

    fun release()
}

/** The alarm that ends a forgotten pause. */
internal interface PauseAlarm {
    fun start()

    fun cancel()
}

/**
 * The service's workout logic, free of Android classes so that it runs under JUnit with a virtual
 * clock: command handling, foreground/wake-lock/alarm choreography, and the end-of-workout sequence.
 * [com.baccours.kronos.service.TimerService] is only the shell that wires it to the platform.
 *
 * Main-thread only. [lingerTimer] is a [TickScheduler] used as a one-shot delayed task.
 */
internal class WorkoutSession(
    private val host: SessionHost,
    private val notifier: SessionNotifier,
    private val wakeLock: HeldLock,
    private val pauseAlarm: PauseAlarm,
    private val lingerTimer: TickScheduler,
    private val store: TimerStateStore,
    private val newController: (WorkoutController.Listener) -> WorkoutController
) : WorkoutController.Listener {

    private var controller: WorkoutController? = null
    private var finishedState: TimerState? = null

    /** [config] is null when the start request could not be decoded. One with nothing to run is refused too. */
    fun start(config: TimerConfig?) {
        val current = controller
        if (current != null && current.isActive) {
            // Re-enter foreground for duplicate start requests
            host.enterForeground(current.lastState)
            return
        }
        if (config == null || !config.canStart) {
            host.enterForeground(TimerState())
            store.reportStartFailure()
            host.stopSelf()
            return
        }
        lingerTimer.cancel()
        // Enter foreground immediately before background setup
        if (!host.enterForeground(TimerState(status = TimerStatus.RUNNING, config = config))) {
            store.reportStartFailure()
            host.stopSelf()
            return
        }
        wakeLock.acquire()
        (controller ?: newController(this).also { controller = it }).start(config)
    }

    fun pause() {
        val c = controller ?: return
        // Not while finished (the lock is kept for the closing audio) or already paused.
        if (c.status != TimerStatus.RUNNING) return
        c.pause()
        wakeLock.release()
        pauseAlarm.start()
    }

    fun resume() {
        val c = controller ?: return
        if (c.status != TimerStatus.PAUSED) return
        pauseAlarm.cancel()
        wakeLock.acquire()
        c.resume()
    }

    fun skip() {
        controller?.skip()
    }

    fun lap() {
        controller?.lap()
    }

    /** Preloads the clips of the running workout again, e.g. once the voice is switched back on. */
    fun prepareCues() {
        controller?.prepareCues()
    }

    fun stop() {
        lingerTimer.cancel()
        pauseAlarm.cancel()
        controller?.stop()
        wakeLock.release()
        store.reset()
        host.leaveForeground(removeNotification = true)
        notifier.cancel()
        host.stopSelf()
    }

    /** A forgotten pause ends the workout; say so, since the app silently returns to Set up. */
    fun endForgottenPause() {
        stop()
        notifier.showPauseTimeout()
    }

    /** Call after every command: one for a workout that no longer exists must not leave an idle service behind. */
    fun endOfCommand() {
        if (controller == null) host.stopSelf()
    }

    /** The service is going away (the process may live on). */
    fun destroy() {
        lingerTimer.cancel()
        pauseAlarm.cancel()
        controller?.stop()
        controller = null
        wakeLock.release()
        // Not after a finished workout, whose results stay on screen; otherwise the UI must not be
        // left showing a timer that no longer runs.
        if (store.timerState.value.status != TimerStatus.FINISHED) store.reset()
    }

    /** Ends the service after the closing audio; the completion notification stays. */
    private fun endAfterFinish() {
        wakeLock.release()
        host.leaveForeground(removeNotification = false)
        // Re-post once detached so it is a normal, dismissible notification.
        finishedState?.let { notifier.show(it) }
        host.stopSelf()
    }

    override fun onStateChanged(state: TimerState) {
        store.updateState(state)
        if (state.status == TimerStatus.RUNNING) wakeLock.renewIfDue()
        if (state.status != TimerStatus.FINISHED) {
            notifier.updateIfChanged(state)
        }
    }

    override fun onFinished(state: TimerState) {
        pauseAlarm.cancel()
        finishedState = state
        notifier.show(state)
        // Stay foreground (wake lock held) until the closing audio has played: from Android 17
        // background audio is silenced once the service leaves the foreground.
        lingerTimer.scheduleAfter(FINISH_LINGER_MS) { endAfterFinish() }
    }

    override fun onLateTick(lateMs: Long) {
        // Log execution delay for Doze/power saving diagnostics
        host.logWarning("A tick ran $lateMs ms late")
    }

    override fun onCueError(cue: Cue, error: Exception) {
        host.logError("Cannot play $cue", error)
    }

    override fun onListenerError(callback: String, error: Exception) {
        host.logError("$callback failed", error)
    }

    companion object {
        /** How long the service outlives the end of a workout, for the closing audio. */
        const val FINISH_LINGER_MS = 4_000L
    }
}
