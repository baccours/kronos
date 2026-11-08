package com.baccours.kronos.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.WorkoutController
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerStateStore

/**
 * Foreground service maintaining workout execution in background.
 * Delegates logic to [WorkoutController] and notification updates to [TimerNotifier].
 */
class TimerService : Service(), WorkoutController.Listener {

    private val store: TimerStateStore = TimerRepository
    private val handler = Handler(Looper.getMainLooper())
    private val stopSelfRunnable = Runnable { stopSelf() }

    private lateinit var notifier: TimerNotifier
    private var audio: TimerAudioManager? = null
    private var controller: WorkoutController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = TimerNotifier(this)
        notifier.createChannel()
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
            stopSelf()
            return
        }
        handler.removeCallbacks(stopSelfRunnable)
        // Enter foreground immediately before background setup
        enterForeground(TimerState(status = TimerStatus.RUNNING, config = config))
        acquireWakeLock()
        controllerForStart().start(config)
    }

    private fun controllerForStart(): WorkoutController {
        controller?.let { return it }
        val player = audio ?: TimerAudioManager(this).also { audio = it }
        return WorkoutController(
            cues = player,
            scheduler = HandlerTickScheduler(handler),
            clock = SystemClock::elapsedRealtime,
            listener = this
        ).also { controller = it }
    }

    private fun pauseTimer() {
        controller?.pause()
        releaseWakeLock()
    }

    private fun resumeTimer() {
        val c = controller ?: return
        if (c.status != TimerStatus.PAUSED) return
        acquireWakeLock()
        c.resume()
    }

    private fun stopTimer() {
        handler.removeCallbacks(stopSelfRunnable)
        controller?.stop()
        releaseWakeLock()
        store.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        notifier.cancel()
        stopSelf()
    }

    private fun enterForeground(state: TimerState) {
        val notification = notifier.buildForForeground(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                TimerNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(TimerNotifier.NOTIFICATION_ID, notification)
        }
        inForeground = true
    }

    override fun onStateChanged(state: TimerState) {
        store.updateState(state)
        if (state.status != TimerStatus.FINISHED && inForeground) {
            notifier.updateIfChanged(state)
        }
    }

    override fun onFinished(state: TimerState) {
        releaseWakeLock()
        // Detach foreground state while retaining completion notification
        stopForeground(STOP_FOREGROUND_DETACH)
        inForeground = false
        notifier.show(state)
        // Linger briefly for audio completion before stopping service
        handler.removeCallbacks(stopSelfRunnable)
        handler.postDelayed(stopSelfRunnable, FINISH_LINGER_MS)
    }

    override fun onLateTick(lateMs: Long) {
        // Log execution delay for Doze/power saving diagnostics
        Log.w(TAG, "A tick ran $lateMs ms late")
    }

    override fun onCueError(cue: Cue, error: Exception) {
        Log.e(TAG, "Cannot play $cue", error)
    }

    // ------------------------------------------------------------------ wake lock

    private fun acquireWakeLock() {
        val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:timer")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        // Safety net so a bug can never keep the CPU awake forever.
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        controller?.stop()
        controller = null
        releaseWakeLock()
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
        private const val FINISH_LINGER_MS = 8_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
    }
}
