package com.baccours.kronos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.baccours.kronos.audio.ClipCatalog
import com.baccours.kronos.audio.ClipLibrary
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.service.WorkoutCommands
import com.baccours.kronos.ui.running.RunningScreen
import com.baccours.kronos.ui.setup.SetupScreen
import com.baccours.kronos.ui.theme.KronosTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: WorkoutViewModel by viewModels()
    private val commands by lazy { WorkoutCommands(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Preload fixed phrases off the main thread so "Get ready" starts instantly.
        lifecycleScope.launch(Dispatchers.Default) {
            ClipLibrary.get(applicationContext).preload(ClipCatalog.clipsForAll(ClipCatalog.PHRASES))
        }
        setContent {
            KronosTheme {
                val status by viewModel.status.collectAsStateWithLifecycle()

                // Encoded workout config awaiting notification permission response across recreations.
                var pendingConfig by rememberSaveable { mutableStateOf<String?>(null) }
                var askedForNotifications by rememberSaveable { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        // Start workout regardless of permission; if denied, it runs without notifications.
                        val config = TimerConfigCodec.decode(pendingConfig)
                        pendingConfig = null
                        if (config != null) startWorkout(config)
                    }
                )

                if (status == TimerStatus.NOT_STARTED) {
                    SetupScreen(
                        onStartTimer = { config ->
                            if (needsNotificationPermission() && !askedForNotifications) {
                                // Prompt for notification permission when starting a workout.
                                askedForNotifications = true
                                pendingConfig = TimerConfigCodec.encode(config)
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                startWorkout(config)
                            }
                        }
                    )
                } else {
                    RunningScreen(
                        viewModel = viewModel,
                        onPause = { commands.pause() },
                        onResume = { commands.resume() },
                        onStop = {
                            // Dismiss finished workout results.
                            if (status == TimerStatus.FINISHED) commands.dismissFinished() else commands.stop()
                        },
                        onSkip = { commands.skip() },
                        onLap = { commands.lap() }
                    )
                }
            }
        }
    }

    private fun startWorkout(config: TimerConfig) {
        if (!commands.start(config)) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
