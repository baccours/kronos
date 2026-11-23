package com.baccours.kronos

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.service.WorkoutCommands
import com.baccours.kronos.ui.running.RunningActions
import com.baccours.kronos.ui.running.RunningScreen
import com.baccours.kronos.ui.running.WorkoutViewModel
import com.baccours.kronos.ui.setup.SetupScreen
import com.baccours.kronos.ui.setup.SetupViewModel
import com.baccours.kronos.ui.setup.rememberStartWithNotificationPermission
import com.baccours.kronos.ui.theme.KronosTheme
import com.baccours.kronos.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val viewModel: WorkoutViewModel by viewModels {
        val store = appContainer.workoutStateStore
        viewModelFactory { initializer { WorkoutViewModel(store) } }
    }
    private val setupViewModel: SetupViewModel by viewModels {
        val storage = appContainer.timerConfigRepository
        viewModelFactory { initializer { SetupViewModel(storage) } }
    }
    private val settingsRepository get() = appContainer.settingsRepository
    private val commands by lazy { WorkoutCommands(this, appContainer.workoutStateStore) }
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
            val saved by settingsRepository.settings.collectAsStateWithLifecycle()
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

                val onStart = rememberStartWithNotificationPermission(::startWorkout)

                if (status == TimerStatus.NOT_STARTED) {
                    SetupScreen(
                        viewModel = setupViewModel,
                        settings = settings,
                        onSettingsChange = settingsRepository::update,
                        onStartTimer = onStart
                    )
                } else {
                    RunningScreen(
                        viewModel = viewModel,
                        actions = runningActions,
                        settings = settings,
                        onSettingsChange = settingsRepository::update
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
}
