package com.baccours.kronos.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.R
import com.baccours.kronos.WorkoutViewModel
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.formatDuration
import com.baccours.kronos.phaseLabel
import com.baccours.kronos.ui.icons.Close
import com.baccours.kronos.ui.icons.Icons

// Layout structure: Top bar, Set/Round stats, active phase timer, lap section, overall progress, controls.

private val SegmentBarHeight = 12.dp
private val OverallBarHeight = 6.dp
private val MaxTimeFontSize = 160.dp

private const val TrackAlpha = 0.18f
/** Alpha value ensuring >=4.5:1 text contrast across phase colors. */
private const val SecondaryTextAlpha = 0.85f

/** Monospace digit advance width in em. */
private const val GlyphWidthEm = 0.63f
private const val LineHeightEm = 1.25f

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

@Composable
fun RunningScreen(
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit = {},
    onLap: () -> Unit = {},
    viewModel: WorkoutViewModel = viewModel()
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

    val requestStop = {
        if (state.status == TimerStatus.RUNNING) {
            onPause()
            pausedByDialog = true
        }
        showStopDialog = true
    }
    val dismissStop = {
        showStopDialog = false
        if (pausedByDialog) {
            pausedByDialog = false
            onResume()
        }
    }

    BackHandler {
        when (state.status) {
            TimerStatus.RUNNING -> onPause()
            TimerStatus.PAUSED -> requestStop()
            else -> onStop()
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
                state.isFinished -> FinishedView(state, landscape, onExit = onStop)
                landscape -> LandscapeContent(
                    state, animatedColors, requestStop, onPause, onResume, onSkip, onLap
                )
                else -> PortraitContent(
                    state, animatedColors, requestStop, onPause, onResume, onSkip, onLap
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
                TextButton(onClick = {
                    showStopDialog = false
                    pausedByDialog = false
                    onStop()
                }) { Text(stringResource(R.string.stop_dialog_confirm)) }
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
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onLap: () -> Unit
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
                    state = state,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))
            OverallProgress(state)
            Spacer(Modifier.height(16.dp))
            Controls(state, colors, compact = false, onPause, onResume, onSkip, onLap)
        }
        CloseButton(onClose, Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun LandscapeContent(
    state: RunningUiState,
    colors: PhaseColors,
    onClose: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onLap: () -> Unit
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
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    OverallProgress(state)
                }
                Spacer(Modifier.height(8.dp))
                Controls(state, colors, compact = true, onPause, onResume, onSkip, onLap)
            }
        }
        CloseButton(onClose, Modifier.align(Alignment.TopStart))
    }
}

// ---------------------------------------------------------------------------- pieces

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClose, modifier = modifier) {
        Icon(Icons.Close, contentDescription = stringResource(R.string.action_close))
    }
}

/** Displays set and round indicators. Hidden when total count is 1. */
@Composable
private fun SetRoundStats(state: RunningUiState, compact: Boolean) {
    val showSet = state.totalSets > 1
    val showRound = if (state.isForTime) state.phase == Phase.WORK else state.totalRounds > 1
    if (!showSet && !showRound) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSet) {
            Stat(
                label = stringResource(R.string.running_set),
                value = "${state.currentSet}/${state.totalSets}",
                compact = compact
            )
        }
        if (showSet && showRound) {
            Text(
                text = "·",
                style = MaterialTheme.typography.headlineLarge,
                color = LocalContentColor.current.copy(alpha = SecondaryTextAlpha),
            )
        }
        if (showRound) {
            Stat(
                label = stringResource(R.string.running_round),
                value = if (state.isForTime) {
                    "${state.currentRound}"
                } else {
                    "${state.currentRound}/${state.totalRounds}"
                },
                compact = compact
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = LocalContentColor.current.copy(alpha = SecondaryTextAlpha),
            maxLines = 1
        )
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

/** Displays active phase header, main timer readout, and segment progress bar. */
@Composable
private fun NowGroup(
    state: RunningUiState,
    modifier: Modifier = Modifier,
    headerInset: Dp = 0.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PhaseHeader(state, modifier = Modifier.padding(horizontal = headerInset))
        Spacer(Modifier.height(8.dp))
        TimerReadout(
            text = state.displayTime,
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        SegmentProgress(state)
    }
}

@Composable
private fun PhaseHeader(state: RunningUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val subtitle = if (state.isPaused) {
        stringResource(R.string.running_paused)
    } else {
        nextText(state)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.phaseLabel(state.phase, state.workIndex),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = LocalContentColor.current.copy(alpha = SecondaryTextAlpha),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun nextText(state: RunningUiState): String {
    val next = state.next ?: return stringResource(R.string.running_next_finish)
    if (state.isForTime && next.phase == Phase.WORK) {
        return stringResource(R.string.running_next_set, next.set)
    }
    val label = LocalContext.current.phaseLabel(next.phase, next.workIndex)
    return if (next.seconds > 0) {
        stringResource(R.string.running_next_with_time, label, next.seconds.formatDuration())
    } else {
        stringResource(R.string.running_next, label)
    }
}

/** Monospace timer readout dynamically scaled to fit container bounds without line wrapping. */
@Composable
private fun TimerReadout(text: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val byWidth = constraints.maxWidth / (text.length * GlyphWidthEm)
        val byHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight / LineHeightEm
        } else {
            Float.MAX_VALUE
        }
        val fontSize = with(density) {
            val px = minOf(byWidth, byHeight, MaxTimeFontSize.toPx())
            px.toDp().toSp()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp
            ),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

/** Progress bar for active segment smoothly animated over time. */
@Composable
private fun SegmentProgress(state: RunningUiState) {
    val now = state.segmentProgress
    val next = state.segmentProgressNext
    if (now == null || next == null) {
        Spacer(Modifier.height(SegmentBarHeight))
        return
    }
    val animated = remember(state.segmentIndex) { Animatable(now) }
    val running = state.status == TimerStatus.RUNNING
    val stepMs = state.progressStepMs
    LaunchedEffect(state.segmentIndex, state.elapsedSeconds, running) {
        animated.snapTo(now)
        if (running) animated.animateTo(next, tween(stepMs, easing = LinearEasing))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { animated.value },
            modifier = Modifier
                .weight(1f)
                .height(SegmentBarHeight)
                .clip(RoundedCornerShape(SegmentBarHeight / 2)),
            color = LocalContentColor.current,
            trackColor = LocalContentColor.current.copy(alpha = TrackAlpha),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        if (!state.timeCap.isNullOrEmpty()) {
            Text(
                text = state.timeCap,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = LocalContentColor.current.copy(alpha = SecondaryTextAlpha)
            )
        }
    }
}

/** Progress bar displaying overall workout elapsed and remaining time. */
@Composable
private fun OverallProgress(state: RunningUiState) {
    val progress = state.overallProgress
    val elapsed = if (state.phase != Phase.READY) state.totalElapsed else null
    val end = state.totalRemaining?.let { "-$it" }
    if (progress == null && elapsed == null && end == null) return

    val textStyle = MaterialTheme.typography.titleMedium
    val textColor = LocalContentColor.current.copy(alpha = SecondaryTextAlpha)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = elapsed.orEmpty(),
            style = textStyle,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(OverallBarHeight)
                    .clip(RoundedCornerShape(OverallBarHeight / 2)),
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = TrackAlpha),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        Text(
            text = end.orEmpty(),
            style = textStyle,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}
