package com.baccours.kronos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.service.TimerService
import com.baccours.kronos.ui.running.RunningScreen
import com.baccours.kronos.ui.setup.SetupScreen
import com.baccours.kronos.ui.theme.KronosTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KronosTheme {
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasNotificationPermission = isGranted
                    }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                if (uiState.timerStatus == TimerStatus.NOT_STARTED) {
                    SetupScreen(
                        onStartTimer = { config ->
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_START
                                putExtra(TimerService.EXTRA_CONFIG, config)
                            }
                            ContextCompat.startForegroundService(context, intent)
                        }
                    )
                } else {
                    RunningScreen(
                        onPause = { sendServiceAction(TimerService.ACTION_PAUSE) },
                        onResume = { sendServiceAction(TimerService.ACTION_RESUME) },
                        onStop = { sendServiceAction(TimerService.ACTION_STOP) },
                        onSkip = { sendServiceAction(TimerService.ACTION_SKIP) },
                        onLap = { sendServiceAction(TimerService.ACTION_LAP) }
                    )
                }
            }
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }
}
