package com.baccours.kronos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.service.WorkoutCommands
import com.baccours.kronos.ui.running.RunningActions
import com.baccours.kronos.ui.running.RunningScreen
import com.baccours.kronos.ui.running.WorkoutViewModel
import com.baccours.kronos.ui.settings.SettingsViewModel
import com.baccours.kronos.ui.setup.SetupScreen
import com.baccours.kronos.ui.theme.KronosTheme
import com.baccours.kronos.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val viewModel: WorkoutViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val commands by lazy { WorkoutCommands(this) }
    private val runningActions by lazy {
        RunningActions(commands::pause, commands::resume, commands::stop, commands::skip, commands::lap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default bars until the settings are loaded; re-applied with the theme below.
        enableEdgeToEdge()
        setContent {
            // The service reports start failures asynchronously (commands.start() only sees the intent).
            LaunchedEffect(Unit) {
                viewModel.startFailures.collect { showStartFailed() }
            }
            val saved by settingsViewModel.settings.collectAsStateWithLifecycle()
            // Draw nothing until the saved settings are loaded, so the first frame has the right theme.
            val settings = saved ?: return@setContent
            val dark = settings.theme.isDark()
            // System bar icons follow the app theme, which may differ from the system one.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark }
                )
            }
            KronosTheme(settings.theme) {
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
                        settings = settings,
                        onSettingsChange = settingsViewModel::update,
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
                        actions = runningActions,
                        settings = settings,
                        onSettingsChange = settingsViewModel::update
                    )
                }
            }
        }
    }

    private fun startWorkout(config: TimerConfig) {
        if (!commands.start(config)) showStartFailed()
    }

    private fun showStartFailed() {
        Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
    }

    private companion object {
        // Scrims for the 3-button navigation bar, as in the Android sample apps.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
