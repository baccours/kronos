package com.baccours.kronos.ui.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec

/**
 * Wraps [startWorkout] so that the first start also asks for the notification permission (API 33+).
 * The workout starts whatever the answer: without the permission it just has no notification.
 */
@Composable
fun rememberStartWithNotificationPermission(startWorkout: (TimerConfig) -> Unit): (TimerConfig) -> Unit {
    val context = LocalContext.current
    val currentStart by rememberUpdatedState(startWorkout)
    // The config waiting for the answer is kept encoded so that it survives recreation.
    var pendingConfig by rememberSaveable { mutableStateOf<String?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val config = TimerConfigCodec.decode(pendingConfig)
        pendingConfig = null
        if (config != null) currentStart(config)
    }

    return { config ->
        if (!asked && context.needsNotificationPermission()) {
            asked = true
            pendingConfig = TimerConfigCodec.encode(config)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            currentStart(config)
        }
    }
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
