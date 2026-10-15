package com.baccours.kronos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.baccours.kronos.MainActivity
import com.baccours.kronos.R
import com.baccours.kronos.engine.EngineResult
import com.baccours.kronos.engine.TimerEngine
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.formatDuration
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.ui.phaseLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the workout. All timing/state lives in [TimerEngine]; the
 * service only feeds it the clock, plays the cues it returns and mirrors its state to the
 * repository and the notification.
 */
class TimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var finishJob: Job? = null

    private var engine: TimerEngine? = null
    private var audio: TimerAudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationKey: Long = Long.MIN_VALUE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CONFIG, TimerConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CONFIG)
                }
                startTimer(config)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_SKIP -> engine?.let { publish(it.skip(now())) }
            ACTION_LAP -> engine?.let { publish(it.lap(now())) }
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ commands

    private fun startTimer(config: TimerConfig?) {
        val running = engine?.status
        if (running == TimerStatus.RUNNING || running == TimerStatus.PAUSED) {
            // Duplicate start request: still honour the startForegroundService() contract.
            enterForeground(TimerRepository.timerState.value)
            return
        }
        if (config == null) {
            enterForeground(TimerState())
            stopSelf()
            return
        }
        finishJob?.cancel()
        val newEngine = TimerEngine(config)
        engine = newEngine
        audio = audio ?: TimerAudioManager(this)
        // Get this workout's voice clips ready, in the order they will be needed. The "get ready"
        // countdown gives the decoder a head start; clips decoded earlier are kept in memory.
        audio?.prepare(newEngine.speechNeeded())

        val result = newEngine.start(now())
        TimerRepository.updateState(result.state)
        lastNotificationKey = notificationKey(result.state)
        enterForeground(result.state)

        acquireWakeLock()
        result.cues.forEach { audio?.play(it) }
        startTicker()
    }

    private fun enterForeground(state: TimerState) {
        val notification = createNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun pauseTimer() {
        val e = engine ?: return
        tickerJob?.cancel()
        audio?.stopSpeaking()
        releaseWakeLock()
        publish(e.pause(now()))
    }

    private fun resumeTimer() {
        val e = engine ?: return
        if (e.status != TimerStatus.PAUSED) return
        acquireWakeLock()
        publish(e.resume(now()))
        startTicker()
    }

    private fun stopTimer() {
        tickerJob?.cancel()
        finishJob?.cancel()
        engine = null
        releaseWakeLock()
        audio?.stopSpeaking()
        TimerRepository.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    // ------------------------------------------------------------------ ticking

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val e = engine ?: break
                // Sleep until just after the next second boundary instead of polling.
                delay(e.millisUntilNextTick(now()))
                val current = engine ?: break
                val result = current.tick(now())
                publish(result)
                if (result.state.status != TimerStatus.RUNNING) break
            }
        }
    }

    private fun publish(result: EngineResult) {
        val state = result.state
        TimerRepository.updateState(state)
        result.cues.forEach { audio?.play(it) }

        if (state.status == TimerStatus.FINISHED) {
            onFinished(state)
            return
        }
        // The notification shows a system-driven chronometer, so it only needs to be
        // re-posted when something other than the passing seconds changes.
        val key = notificationKey(state)
        if (key != lastNotificationKey) {
            lastNotificationKey = key
            notify(state)
        }
    }

    private fun onFinished(state: TimerState) {
        tickerJob?.cancel()
        releaseWakeLock()
        // Leave the service-foreground state but keep a (dismissable) "finished" notification.
        stopForeground(STOP_FOREGROUND_DETACH)
        notify(state)
        // Give the celebration sound and "Well done" time to play before the service goes away.
        finishJob?.cancel()
        finishJob = serviceScope.launch {
            delay(FINISH_LINGER_MS)
            stopSelf()
        }
    }

    // ------------------------------------------------------------------ wake lock

    private fun acquireWakeLock() {
        val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:timer")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        // Safety net so a bug can never keep the CPU awake forever.
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    // ------------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notificationKey(state: TimerState): Long =
        state.status.ordinal * 1_000_000L + state.segmentIndex * 1_000L + state.laps.size

    private fun notify(state: TimerState) {
        if (state.status == TimerStatus.NOT_STARTED) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(state))
    }

    private fun createNotification(state: TimerState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
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
            getString(R.string.notification_finished_title)
        } else {
            phaseLabel(state.phase, state.workIndex)
        }

        val sets = cfg?.sets ?: 1
        val rounds = cfg?.rounds ?: 1
        val progress = if (isForTime) {
            getString(R.string.notification_progress_for_time, state.currentSet, sets, state.currentRound)
        } else {
            getString(R.string.notification_progress_timer, state.currentSet, sets, state.currentRound, rounds)
        }
        val shownSeconds = if (countsDown) state.remainingSeconds else state.elapsedSeconds
        val text = when {
            finished -> getString(R.string.notification_finished_text)
            paused -> getString(R.string.notification_paused, shownSeconds.formatDuration())
            else -> progress
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
            // Let the system render the ticking time: no per-second notification updates.
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
                    builder.addAction(0, getString(R.string.action_round), servicePendingIntent(ACTION_LAP))
                }
                builder.addAction(0, getString(R.string.action_pause), servicePendingIntent(ACTION_PAUSE))
            }
            TimerStatus.PAUSED ->
                builder.addAction(0, getString(R.string.action_resume), servicePendingIntent(ACTION_RESUME))
            else -> Unit
        }
        if (!finished) {
            builder.addAction(0, getString(R.string.action_stop), servicePendingIntent(ACTION_STOP))
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    override fun onDestroy() {
        tickerJob?.cancel()
        finishJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        audio?.release()
        audio = null
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

        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1
        private const val FINISH_LINGER_MS = 8_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
    }
}
