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
        // Decode the fixed phrases now, in the background, so "Get ready" is instant at Start.
        // (Off the main thread: even finding the clips touches the asset directory.)
        lifecycleScope.launch(Dispatchers.Default) {
            ClipLibrary.get(applicationContext).preload(ClipCatalog.clipsForAll(ClipCatalog.PHRASES))
        }
        setContent {
            KronosTheme {
                val status by viewModel.status.collectAsStateWithLifecycle()

                // The workout waiting for the answer to the notification permission prompt.
                // Text, so that it survives the Activity being recreated while the prompt is open.
                var pendingConfig by rememberSaveable { mutableStateOf<String?>(null) }
                var askedForNotifications by rememberSaveable { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        // Granted or not, the workout starts: without the permission it only lacks
                        // its notification (it still runs in the foreground service).
                        val config = TimerConfigCodec.decode(pendingConfig)
                        pendingConfig = null
                        if (config != null) startWorkout(config)
                    }
                )

                if (status == TimerStatus.NOT_STARTED) {
                    SetupScreen(
                        onStartTimer = { config ->
                            if (needsNotificationPermission() && !askedForNotifications) {
                                // Asked here, when the person starts a workout and the reason is
                                // obvious, not out of the blue when the app opens.
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
                        onPause = { commands.pause() },
                        onResume = { commands.resume() },
                        onStop = {
                            // A finished workout has nothing left to stop: just close its results.
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
