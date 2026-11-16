package com.baccours.kronos.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.baccours.kronos.audio.TimerAudioManager
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.WorkoutController
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.SettingsStore
import com.baccours.kronos.repository.TimerStateStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service maintaining workout execution in background.
 * Delegates logic to [WorkoutController] and notification updates to [TimerNotifier].
 */
class TimerService : Service(), WorkoutController.Listener {

    private val store: TimerStateStore = TimerStateStore
    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()

    private lateinit var notifier: TimerNotifier
    private lateinit var wakeLock: WakeLockHolder
    private lateinit var pauseTimeout: PauseTimeout
    private var audio: TimerAudioManager? = null
    private var controller: WorkoutController? = null

    private var finishedState: TimerState? = null

    /** Ends the service after the closing audio; the completion notification stays. */
    private val finishRunnable = Runnable {
        wakeLock.release()
        stopForeground(STOP_FOREGROUND_DETACH)
        // Re-post once detached so it is a normal, dismissible notification.
        finishedState?.let { notifier.show(it) }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = TimerNotifier(this)
        notifier.createChannel()
        wakeLock = WakeLockHolder(this, "kronos:timer")
        pauseTimeout = PauseTimeout(this, handler, onTimeout = ::stopTimer)
        // Muting the voice cuts the phrase being spoken; later cues are filtered by the audio manager.
        // Unmuting preloads the clips skipped at start, so the next phrase is not late.
        scope.launch {
            SettingsStore.settings.map { it?.voice }.distinctUntilChanged().collect { voice ->
                if (voice == false) audio?.stopSpeaking() else if (voice == true) controller?.prepareCues()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(TimerConfigCodec.decode(intent.getStringExtra(EXTRA_CONFIG)))
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_SKIP -> controller?.skip()
            ACTION_LAP -> controller?.lap()
            ACTION_STOP -> stopTimer()
        }
        // A command for a workout that no longer exists must not leave an idle service behind.
        if (controller == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun startTimer(config: TimerConfig?) {
        val current = controller
        if (current != null && current.isActive) {
            // Re-enter foreground for duplicate start requests
            enterForeground(current.lastState)
            return
        }
        if (config == null) {
            enterForeground(TimerState())
            store.reportStartFailure()
            stopSelf()
            return
        }
        handler.removeCallbacks(finishRunnable)
        // Enter foreground immediately before background setup
        if (!enterForeground(TimerState(status = TimerStatus.RUNNING, config = config))) {
            store.reportStartFailure()
            stopSelf()
            return
        }
        wakeLock.acquire()
        controllerForStart().start(config)
    }

    private fun controllerForStart(): WorkoutController {
        controller?.let { return it }
        val player = audio ?: TimerAudioManager(this, settings = { SettingsStore.current }).also { audio = it }
        return WorkoutController(
            cues = player,
            scheduler = HandlerTickScheduler(handler),
            clock = SystemClock::elapsedRealtime,
            listener = this
        ).also { controller = it }
    }

    private fun pauseTimer() {
        val c = controller ?: return
        c.pause()
        wakeLock.release()
        if (c.status == TimerStatus.PAUSED) pauseTimeout.start()
    }

    private fun resumeTimer() {
        val c = controller ?: return
        if (c.status != TimerStatus.PAUSED) return
        pauseTimeout.cancel()
        wakeLock.acquire()
        c.resume()
    }

    private fun stopTimer() {
        handler.removeCallbacks(finishRunnable)
        pauseTimeout.cancel()
        controller?.stop()
        wakeLock.release()
        store.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifier.cancel()
        stopSelf()
    }

    /** Returns false if the system refuses (foreground start not allowed, missing permission). */
    private fun enterForeground(state: TimerState): Boolean {
        val notification = notifier.buildForForeground(state)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    TimerNotifier.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(TimerNotifier.NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Cannot enter the foreground", e)
            false
        }
    }

    override fun onStateChanged(state: TimerState) {
        store.updateState(state)
        if (state.status == TimerStatus.RUNNING) wakeLock.renewIfDue()
        if (state.status != TimerStatus.FINISHED) {
            notifier.updateIfChanged(state)
        }
    }

    override fun onFinished(state: TimerState) {
        pauseTimeout.cancel()
        finishedState = state
        notifier.show(state)
        // Stay foreground (wake lock held) until the closing audio has played: from Android 17
        // background audio is silenced once the service leaves the foreground.
        handler.removeCallbacks(finishRunnable)
        handler.postDelayed(finishRunnable, FINISH_LINGER_MS)
    }

    override fun onLateTick(lateMs: Long) {
        // Log execution delay for Doze/power saving diagnostics
        Log.w(TAG, "A tick ran $lateMs ms late")
    }

    override fun onCueError(cue: Cue, error: Exception) {
        Log.e(TAG, "Cannot play $cue", error)
    }

    override fun onListenerError(callback: String, error: Exception) {
        Log.e(TAG, "$callback failed", error)
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        pauseTimeout.cancel()
        controller?.stop()
        controller = null
        wakeLock.release()
        audio?.release()
        audio = null
        // If the service goes away while the process lives (and not after a finished workout, whose
        // results stay on screen), the UI must not be left showing a timer that no longer runs.
        if (store.timerState.value.status != TimerStatus.FINISHED) store.reset()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.baccours.kronos.START"
        const val ACTION_PAUSE = "com.baccours.kronos.PAUSE"
        const val ACTION_RESUME = "com.baccours.kronos.RESUME"
        const val ACTION_STOP = "com.baccours.kronos.STOP"
        const val ACTION_SKIP = "com.baccours.kronos.SKIP"
        const val ACTION_LAP = "com.baccours.kronos.LAP"
        const val EXTRA_CONFIG = "EXTRA_CONFIG"

        private const val TAG = "TimerService"
        private const val FINISH_LINGER_MS = 4_000L
    }
}
