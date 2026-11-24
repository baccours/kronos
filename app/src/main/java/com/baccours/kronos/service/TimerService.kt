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
import com.baccours.kronos.appContainer
import com.baccours.kronos.audio.TimerAudioManager
import com.baccours.kronos.engine.WorkoutController
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service keeping a workout running in the background. A thin shell: the logic lives in
 * [WorkoutSession] (unit-tested with a virtual clock), the platform pieces are wired in here.
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val container by lazy { appContainer }

    private lateinit var notifier: TimerNotifier
    private lateinit var session: WorkoutSession
    private var audio: TimerAudioManager? = null

    /** ID of the latest start request, so a stop never kills a start still in flight. */
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = TimerNotifier(this)
        notifier.createChannel()
        session = WorkoutSession(
            host = AndroidHost(),
            notifier = notifier,
            wakeLock = WakeLockHolder(this, "kronos:timer"),
            pauseAlarm = PauseTimeout(this, handler, onTimeout = { session.endForgottenPause() }),
            lingerTimer = HandlerTickScheduler(handler),
            store = container.workoutStateStore,
            newController = ::newController
        )
        // Muting the voice cuts the phrase being spoken; later cues are filtered by the audio manager.
        // Unmuting preloads the clips skipped at start, so the next phrase is not late.
        scope.launch {
            container.settingsRepository.settings.map { it?.voice }.distinctUntilChanged().collect { voice ->
                if (voice == false) audio?.stopSpeaking() else if (voice == true) session.prepareCues()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> session.start(TimerConfigCodec.decode(intent.getStringExtra(EXTRA_CONFIG)))
            ACTION_PAUSE -> session.pause()
            ACTION_RESUME -> session.resume()
            ACTION_SKIP -> session.skip()
            ACTION_LAP -> session.lap()
            ACTION_STOP -> session.stop()
        }
        session.endOfCommand()
        return START_NOT_STICKY
    }

    private fun newController(listener: WorkoutController.Listener): WorkoutController {
        val player = audio ?: TimerAudioManager(this, settings = { container.settingsRepository.current }).also { audio = it }
        return WorkoutController(
            cues = player,
            scheduler = HandlerTickScheduler(handler),
            clock = SystemClock::elapsedRealtime,
            listener = listener
        )
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        session.destroy()
        audio?.release()
        audio = null
        super.onDestroy()
    }

    /** The platform side of [SessionHost]. */
    private inner class AndroidHost : SessionHost {

        /** Returns false if the system refuses (foreground start not allowed, missing permission). */
        override fun enterForeground(state: TimerState): Boolean {
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

        override fun leaveForeground(removeNotification: Boolean) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
        }

        override fun stopSelf() {
            // No-op if a newer start request is pending.
            stopSelfResult(lastStartId)
        }

        override fun logWarning(message: String) {
            Log.w(TAG, message)
        }

        override fun logError(message: String, error: Throwable) {
            Log.e(TAG, message, error)
        }
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
    }
}
