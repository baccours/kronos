package com.baccours.kronos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.baccours.kronos.MainActivity
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerStatus
import com.baccours.kronos.repository.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    
    private lateinit var audioManager: TimerAudioManager
    private val channelId = "timer_channel"
    private val notificationId = 1

    private var itemStartTimeMillis: Long = 0
    private var pausedTimeAtMillis: Long = 0

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = TimerAudioManager(this)
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
            ACTION_STOP -> stopTimer()
            ACTION_SKIP -> moveToNext()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(config: TimerConfig?) {
        if (config == null) return
        
        TimerRepository.reset()
        
        TimerRepository.updateState(TimerState(
            status = TimerStatus.RUNNING,
            config = config,
            isReadyCountdown = true,
            readyCountdownSeconds = 10,
            remainingSeconds = 10,
            elapsedSeconds = 0,
            currentItemIndex = 0,
            currentRound = 1,
            currentSet = 1
        ))
        
        itemStartTimeMillis = SystemClock.elapsedRealtime()

        val notification = createNotification(TimerRepository.timerState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(notificationId, notification)
        }

        audioManager.speak("Get ready. 10 seconds.")
        
        startTimerTicker()
    }

    private fun pauseTimer() {
        val currentState = TimerRepository.timerState.value
        if (currentState.status == TimerStatus.RUNNING) {
            timerJob?.cancel()
            pausedTimeAtMillis = SystemClock.elapsedRealtime()
            TimerRepository.updateState(currentState.copy(status = TimerStatus.PAUSED))
            updateNotification(TimerRepository.timerState.value)
        }
    }

    private fun resumeTimer() {
        val currentState = TimerRepository.timerState.value
        if (currentState.status == TimerStatus.PAUSED) {
            val pauseDuration = SystemClock.elapsedRealtime() - pausedTimeAtMillis
            itemStartTimeMillis += pauseDuration
            TimerRepository.updateState(currentState.copy(status = TimerStatus.RUNNING))
            startTimerTicker()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        TimerRepository.updateState(TimerRepository.timerState.value.copy(status = TimerStatus.NOT_STARTED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(200)
                updateTimerLogic()
            }
        }
    }

    private fun updateTimerLogic() {
        val currentState = TimerRepository.timerState.value
        val config = currentState.config ?: return
        if (currentState.status != TimerStatus.RUNNING) return

        val now = SystemClock.elapsedRealtime()
        val elapsedSinceItemStart = ((now - itemStartTimeMillis) / 1000).toInt()
        
        if (currentState.isReadyCountdown) {
            val remaining = (10 - elapsedSinceItemStart).coerceAtLeast(0)
            if (remaining != currentState.remainingSeconds) {
                TimerRepository.updateState(currentState.copy(remainingSeconds = remaining))
                checkReadyCountdownCues(remaining)
                if (remaining == 0) {
                    startFirstItem()
                } else {
                    updateNotification(TimerRepository.timerState.value)
                }
            }
            return
        }

        val totalSeconds = if (currentState.isRestBetweenSets) {
            config.restBetweenSetsSeconds
        } else {
            currentState.currentItem?.durationSeconds ?: 0
        }
        
        if (config.mode == TimerMode.TIMER || currentState.isRestBetweenSets) {
            val remaining = (totalSeconds - elapsedSinceItemStart).coerceAtLeast(0)
            if (remaining != currentState.remainingSeconds) {
                TimerRepository.updateState(currentState.copy(remainingSeconds = remaining))
                checkAudioCues(remaining, totalSeconds)
                
                if (remaining == 0) {
                    moveToNext()
                } else {
                    updateNotification(TimerRepository.timerState.value)
                }
            }
        } else {
            // For Time mode
            if (elapsedSinceItemStart != currentState.elapsedSeconds) {
                TimerRepository.updateState(currentState.copy(elapsedSeconds = elapsedSinceItemStart))
                // For Time Cues
                if (totalSeconds > 0 && elapsedSinceItemStart == totalSeconds / 2) {
                    audioManager.speak("Halfway")
                }
                
                updateNotification(TimerRepository.timerState.value)
                // Note: For Time doesn't automatically move to next unless it's a "Rest" item with duration
                if (currentState.currentItem?.type == TaskType.REST && elapsedSinceItemStart >= totalSeconds) {
                    moveToNext()
                }
            }
        }
    }

    private fun startFirstItem() {
        val currentState = TimerRepository.timerState.value
        val config = currentState.config ?: return
        val firstItem = config.items.first()
        
        TimerRepository.updateState(currentState.copy(
            isReadyCountdown = false,
            remainingSeconds = if (config.mode == TimerMode.TIMER) firstItem.durationSeconds else 0,
            elapsedSeconds = 0
        ))
        itemStartTimeMillis = SystemClock.elapsedRealtime()
        audioManager.playStartBuzzer()
        audioManager.speak(firstItem.name.ifBlank { firstItem.type.toString() })
    }

    private fun checkReadyCountdownCues(remaining: Int) {
        if (remaining in 1..3) {
            audioManager.playBeep()
            audioManager.speak(remaining.toString())
        }
    }

    private fun moveToNext() {
        val currentState = TimerRepository.timerState.value
        val config = currentState.config ?: return
        
        if (config.mode == TimerMode.FOR_TIME && !currentState.isRestBetweenSets && currentState.currentItem?.type != TaskType.REST) {
            val newRoundTimes = currentState.roundTimes + currentState.elapsedSeconds
            TimerRepository.updateState(currentState.copy(roundTimes = newRoundTimes))
        }

        if (currentState.isRestBetweenSets) {
            // Finished rest between sets, start next set
            val nextSet = currentState.currentSet + 1
            val firstItem = config.items.first()
            TimerRepository.updateState(currentState.copy(
                isRestBetweenSets = false,
                currentSet = nextSet,
                currentRound = 1,
                currentItemIndex = 0,
                remainingSeconds = if (config.mode == TimerMode.TIMER) firstItem.durationSeconds else 0,
                elapsedSeconds = 0
            ))
            itemStartTimeMillis = SystemClock.elapsedRealtime()
            audioManager.playStartBuzzer()
            audioManager.speak("Set $nextSet started. ${firstItem.name.ifBlank { firstItem.type.name }}")
            return
        }

        var nextItemIndex = currentState.currentItemIndex + 1
        var nextRound = currentState.currentRound
        val nextSet = currentState.currentSet
        var triggerRestBetweenSets = false

        if (nextItemIndex >= config.items.size) {
            nextItemIndex = 0
            nextRound++
            if (nextRound > config.roundsPerSet) {
                if (nextSet < config.sets) {
                    triggerRestBetweenSets = true
                } else {
                    finishTimer()
                    return
                }
            }
        }

        if (triggerRestBetweenSets) {
            TimerRepository.updateState(currentState.copy(
                isRestBetweenSets = true,
                remainingSeconds = config.restBetweenSetsSeconds,
                elapsedSeconds = 0
            ))
            itemStartTimeMillis = SystemClock.elapsedRealtime()
            audioManager.speak("Set complete. Rest between sets.")
        } else {
            val nextItem = config.items[nextItemIndex]
            TimerRepository.updateState(currentState.copy(
                currentItemIndex = nextItemIndex,
                currentRound = nextRound,
                currentSet = nextSet,
                remainingSeconds = if (config.mode == TimerMode.TIMER) nextItem.durationSeconds else 0,
                elapsedSeconds = 0
            ))
            itemStartTimeMillis = SystemClock.elapsedRealtime()
            audioManager.playStartBuzzer()
            audioManager.speak(nextItem.name.ifBlank { nextItem.type.name })
        }
    }

    private fun finishTimer() {
        timerJob?.cancel()
        TimerRepository.updateState(TimerRepository.timerState.value.copy(status = TimerStatus.FINISHED))
        audioManager.playCelebration()
        audioManager.speak("Well done !")
        updateNotification(TimerRepository.timerState.value)
    }

    private fun checkAudioCues(remaining: Int, total: Int) {
        when (remaining) {
            10 -> if (total >= 30) audioManager.speak("10 seconds remaining")
            3, 2, 1 -> {
                audioManager.playBeep()
            }
            0 -> audioManager.playLongBeep()
        }
        
        if (total >= 60 && remaining == total / 2) {
            audioManager.speak("Halfway")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active timer"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(state: TimerState): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = when {
            state.status == TimerStatus.FINISHED -> "Finished"
            state.isRestBetweenSets -> "Rest Between Sets"
            else -> "${state.currentItem?.type ?: "Work"} - Set ${state.currentSet}/${state.config?.sets ?: 1}"
        }

        val time = if (state.config?.mode == TimerMode.TIMER || state.isRestBetweenSets) {
            "%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60)
        } else {
            "%02d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60)
        }

        val content = if (state.status == TimerStatus.FINISHED) "Great job!" else {
            "Round ${state.currentRound}/${state.config?.roundsPerSet ?: 1} | $time"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(state.status == TimerStatus.RUNNING || state.status == TimerStatus.PAUSED)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.status == TimerStatus.RUNNING) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", getPendingIntent(ACTION_PAUSE))
        } else if (state.status == TimerStatus.PAUSED) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", getPendingIntent(ACTION_RESUME))
        }
        
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", getPendingIntent(ACTION_STOP))

        return builder.build()
    }

    private fun updateNotification(state: TimerState) {
        if (state.status == TimerStatus.NOT_STARTED) return
        val notification = createNotification(state)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        audioManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SKIP = "ACTION_SKIP"
        const val EXTRA_CONFIG = "EXTRA_CONFIG"
    }
}
