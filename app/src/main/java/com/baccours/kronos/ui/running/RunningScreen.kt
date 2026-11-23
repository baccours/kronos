package com.baccours.kronos.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baccours.kronos.R
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.ui.icons.Close
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.settings.SettingsMenuButton
import com.baccours.kronos.ui.settings.SettingsUpdater

// Layout structure: Top bar, Set/Round stats, active phase timer, lap section, overall progress, controls.

@Immutable
internal data class PhaseColors(val container: Color, val content: Color)

@Composable
private fun phaseColors(state: RunningUiState): PhaseColors {
    val scheme = MaterialTheme.colorScheme
    return when {
        state.isFinished -> PhaseColors(scheme.surface, scheme.onSurface)
        state.phase == Phase.WORK -> PhaseColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        state.phase == Phase.READY -> PhaseColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> PhaseColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
}

/** Workout commands the running screen can trigger. */
@Immutable
class RunningActions(
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onLap: () -> Unit = {}
)

@Composable
fun RunningScreen(
    viewModel: WorkoutViewModel,
    actions: RunningActions,
    settings: AppSettings = AppSettings(),
    onSettingsChange: SettingsUpdater = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keep the screen on only while the timer is actually running.
    val view = LocalView.current
    val keepScreenOn = state.status == TimerStatus.RUNNING
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var showStopDialog by rememberSaveable { mutableStateOf(false) }
    var pausedByDialog by rememberSaveable { mutableStateOf(false) }

    val requestStop = remember(state.status, actions) {
        {
            if (state.status == TimerStatus.RUNNING) {
                actions.onPause()
                pausedByDialog = true
            }
            showStopDialog = true
        }
    }

    val dismissStop = remember(pausedByDialog, actions) {
        {
            showStopDialog = false
            if (pausedByDialog) {
                pausedByDialog = false
                actions.onResume()
            }
        }
    }

    val confirmStop = remember(actions) {
        {
            showStopDialog = false
            pausedByDialog = false
            actions.onStop()
        }
    }

    BackHandler {
        when (state.status) {
            TimerStatus.RUNNING -> actions.onPause()
            TimerStatus.PAUSED -> requestStop()
            else -> actions.onStop()
        }
    }

    val colors = phaseColors(state)
    val container by animateColorAsState(colors.container, tween(300), label = "phaseContainer")
    val content by animateColorAsState(colors.content, tween(300), label = "phaseContent")
    val animatedColors = PhaseColors(container, content)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = container,
        contentColor = content
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val landscape = maxWidth > maxHeight
            when {
                state.isFinished -> FinishedView(state, landscape, onExit = actions.onStop)
                landscape -> LandscapeContent(
                    state, animatedColors, requestStop, actions, settings, onSettingsChange
                )
                else -> PortraitContent(
                    state, animatedColors, requestStop, actions, settings, onSettingsChange
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = dismissStop,
            title = { Text(stringResource(R.string.stop_dialog_title)) },
            text = { Text(stringResource(R.string.stop_dialog_text)) },
            confirmButton = {
                TextButton(onClick = confirmStop) {
                    Text(stringResource(R.string.stop_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = dismissStop) {
                    Text(stringResource(R.string.stop_dialog_dismiss))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------- layouts

@Composable
private fun PortraitContent(
    state: RunningUiState,
    colors: PhaseColors,
    onClose: () -> Unit,
    actions: RunningActions,
    settings: AppSettings,
    onSettingsChange: SettingsUpdater
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SetRoundStats(state, compact = false)
            NowGroup(state, modifier = Modifier.weight(1f))
            if (state.isForTime) {
                Spacer(Modifier.height(12.dp))
                LapSection(
                    lapTime = state.lapTime,
                    currentLap = state.currentLap,
                    laps = state.laps,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))
            OverallProgress(state)
            Spacer(Modifier.height(16.dp))
            Controls(state, colors, compact = false, actions)
        }
        CloseButton(onClose, Modifier.align(Alignment.TopStart))
        SettingsMenuButton(settings, onSettingsChange, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun LandscapeContent(
    state: RunningUiState,
    colors: PhaseColors,
    onClose: () -> Unit,
    actions: RunningActions,
    settings: AppSettings,
    onSettingsChange: SettingsUpdater
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left: what is happening now.
            Column(
                modifier = Modifier
                    .weight(1.7f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The title keeps clear of the floating close button without eating a row.
                NowGroup(state, modifier = Modifier.weight(1f), headerInset = 48.dp)
                if (state.isForTime) {
                    Spacer(Modifier.height(8.dp))
                    OverallProgress(state)
                }
            }
            // Right: context and actions.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SetRoundStats(state, compact = true)
                if (state.isForTime) {
                    LapSection(
                        lapTime = state.lapTime,
                        currentLap = state.currentLap,
                        laps = state.laps,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    OverallProgress(state)
                }
                Spacer(Modifier.height(8.dp))
                Controls(state, colors, compact = true, actions)
            }
        }
        CloseButton(onClose, Modifier.align(Alignment.TopStart))
        SettingsMenuButton(settings, onSettingsChange, Modifier.align(Alignment.TopEnd))
    }
}

// ---------------------------------------------------------------------------- pieces

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClose, modifier = modifier) {
        Icon(Icons.Close, contentDescription = stringResource(R.string.action_close))
    }
}
