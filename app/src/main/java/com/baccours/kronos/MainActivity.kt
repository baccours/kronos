package com.baccours.kronos

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.baccours.kronos.model.WorkoutMode
import com.baccours.kronos.repository.TimerRepository
import com.baccours.kronos.repository.TimerStatus
import com.baccours.kronos.service.WorkoutService
import com.baccours.kronos.ui.running.RunningScreen
import com.baccours.kronos.ui.setup.SetupScreen
import com.baccours.kronos.ui.theme.KronosTheme

class MainActivity : ComponentActivity() {
    private var workoutService: WorkoutService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WorkoutService.LocalBinder
            workoutService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            workoutService = null
        }
    }

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

                val timerState by TimerRepository.timerState.collectAsState()
                val context = LocalContext.current

                if (timerState.status == TimerStatus.NOT_STARTED) {
                    SetupScreen(
                        onStartWorkout = { config ->
                            val intent = Intent(context, WorkoutService::class.java).apply {
                                action = WorkoutService.ACTION_START
                                putExtra(WorkoutService.EXTRA_CONFIG, config)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            bindToService()
                        }
                    )
                } else {
                    RunningScreen(
                        onPause = { sendServiceAction(WorkoutService.ACTION_PAUSE) },
                        onResume = { sendServiceAction(WorkoutService.ACTION_RESUME) },
                        onStop = { sendServiceAction(WorkoutService.ACTION_STOP) },
                        onSkip = { sendServiceAction(WorkoutService.ACTION_SKIP) }
                    )
                }
            }
        }
    }

    private fun bindToService() {
        Intent(this, WorkoutService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindFromService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        // If workout is already running, bind to it
        if (TimerRepository.timerState.value.status != TimerStatus.NOT_STARTED) {
            bindToService()
        }
    }

    override fun onStop() {
        super.onStop()
        unbindFromService()
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, WorkoutService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }
}
