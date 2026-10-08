package com.baccours.kronos.ui.running

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.kronos.R
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.formatDuration
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.icons.Close
import com.baccours.kronos.ui.icons.Done
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Pause
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.icons.SkipNext
import com.baccours.kronos.ui.icons.Trophy
import com.baccours.kronos.ui.phaseLabel

private val SegmentBarHeight = 14.dp
private val MaxTimeFontSize = 160.dp

/** Advance width of a monospace digit, in em (with a small safety margin). */
private const val GlyphWidthEm = 0.63f
private const val LineHeightEm = 1.25f

private data class PhaseColors(val container: Color, val content: Color)

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
    viewModel: RunningViewModel = viewModel()
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderRow(state, onClose)
        OverallProgress(state)
        Spacer(Modifier.height(12.dp))
        PhaseHeader(state)
        TimerReadout(
            text = state.displayTime,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        SegmentProgress(state)
        Spacer(Modifier.height(12.dp))
        TimeDetails(state)
        if (state.isForTime) {
            Spacer(Modifier.height(12.dp))
            LapList(
                laps = state.laps,
                showSet = state.totalSets > 1,
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Controls(state, colors, compact = false, onPause, onResume, onSkip, onLap)
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
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhaseHeader(state)
            TimerReadout(
                text = state.displayTime,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            SegmentProgress(state)
            Spacer(Modifier.height(8.dp))
            TimeDetails(state)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HeaderRow(state, onClose)
            OverallProgress(state)
            Spacer(Modifier.height(8.dp))
            if (state.isForTime) {
                LapList(
                    laps = state.laps,
                    showSet = state.totalSets > 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Controls(state, colors, compact = true, onPause, onResume, onSkip, onLap)
        }
    }
}

// ---------------------------------------------------------------------------- pieces

@Composable
private fun HeaderRow(state: RunningUiState, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Close, contentDescription = stringResource(R.string.action_close))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoColumn(
                label = stringResource(R.string.running_set),
                value = state.currentSet,
                total = state.totalSets
            )
            // For Time has no fixed number of rounds: only show the lap counter during a set.
            if (!state.isForTime || state.phase == Phase.WORK) {
                InfoColumn(
                    label = stringResource(R.string.running_round),
                    value = state.currentRound,
                    total = if (state.isForTime) null else state.totalRounds
                )
            }
        }
        // Balances the close button so the info stays centered.
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: Int,
    total: Int? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalContentColor.current.copy(alpha = 0.75f)
        )
        Row {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alignByBaseline()
            )
            if (total != null) {
                Text(
                    text = "/$total",
                    style = MaterialTheme.typography.bodyLarge,
                    //fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
    }
}

@Composable
private fun OverallProgress(state: RunningUiState) {
    val progress = state.overallProgress ?: return
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = LocalContentColor.current,
        trackColor = LocalContentColor.current.copy(alpha = 0.18f),
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun PhaseHeader(state: RunningUiState) {
    val context = LocalContext.current
    val subtitle = if (state.isPaused) {
        stringResource(R.string.running_paused)
    } else {
        nextText(state)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
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
            color = LocalContentColor.current.copy(alpha = 0.8f),
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

/**
 * The big time readout. The font size is derived from the available space (monospace, so
 * every digit has the same width) which keeps "00:30" and "01:00:00" on one line on any
 * screen size, orientation or font scale, and stops the digits from jittering.
 */
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

/** Bar for the current segment, animated smoothly without recomposing every frame. */
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
    LaunchedEffect(state.segmentIndex, state.elapsedSeconds, running) {
        animated.snapTo(now)
        if (running) animated.animateTo(next, tween(1000, easing = LinearEasing))
    }
    LinearProgressIndicator(
        progress = { animated.value },
        modifier = Modifier
            .fillMaxWidth()
            .height(SegmentBarHeight)
            .clip(RoundedCornerShape(SegmentBarHeight / 2)),
        color = LocalContentColor.current,
        trackColor = LocalContentColor.current.copy(alpha = 0.18f),
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun TimeDetails(state: RunningUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        state.lapTime?.let { lapTime ->
            Text(
                text = stringResource(R.string.running_lap_in_progress, state.currentRound, lapTime),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        state.totalRemaining?.let { remaining ->
            Text(
                text = stringResource(R.string.running_total_remaining, remaining),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        val elapsed = state.totalElapsed
        if (elapsed != null && state.phase != Phase.READY) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.running_total_elapsed, elapsed),
                    style = MaterialTheme.typography.bodyLarge
                )
                state.timeCap?.let { cap ->
                    Text(
                        text = stringResource(R.string.running_time_cap, cap),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LapList(
    laps: List<LapUi>,
    showSet: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(laps, key = { "${it.set}-${it.number}" }) { lap ->
            LapRow(lap, showSet)
        }
    }
}

@Composable
private fun LapRow(lap: LapUi, showSet: Boolean, modifier: Modifier = Modifier) {
    val label = if (showSet) {
        stringResource(R.string.lap_label_set, lap.set, lap.number)
    } else {
        stringResource(R.string.lap_label, lap.number)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LocalContentColor.current.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        lap.delta?.let { delta ->
            Text(
                text = delta,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (lap.isFaster) LocalContentColor.current else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Text(
            text = lap.time,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Controls(
    state: RunningUiState,
    colors: PhaseColors,
    compact: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onLap: () -> Unit
) {
    val bigSize = if (compact) 64.dp else 80.dp
    val smallSize = if (compact) 56.dp else 64.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.canLap) {
            Button(
                onClick = onLap,
                enabled = !state.isPaused,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bigSize),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.content,
                    contentColor = colors.container,
                    disabledContainerColor = colors.content.copy(alpha = 0.2f),
                    disabledContentColor = colors.content.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_round),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = if (state.isPaused) onResume else onPause,
                modifier = Modifier.size(bigSize),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.content,
                    contentColor = colors.container
                )
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Play else Icons.Pause,
                    contentDescription = stringResource(
                        if (state.isPaused) R.string.action_resume else R.string.action_pause
                    ),
                    modifier = Modifier.size(40.dp)
                )
            }

            if (state.isForTime && state.phase == Phase.WORK) {
                FilledTonalButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(smallSize),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.content.copy(alpha = 0.15f),
                        contentColor = colors.content
                    )
                ) {
                    Icon(Icons.Done, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (state.isLastSet) R.string.action_finish else R.string.action_finish_set
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(smallSize),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.content.copy(alpha = 0.15f),
                        contentColor = colors.content
                    )
                ) {
                    Icon(
                        imageVector = Icons.SkipNext,
                        contentDescription = stringResource(R.string.action_skip)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- finished

@Composable
private fun FinishedView(state: RunningUiState, landscape: Boolean, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item(key = "summary") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Trophy,
                        contentDescription = null,
                        modifier = Modifier.size(if (landscape) 64.dp else 112.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(if (landscape) 8.dp else 24.dp))
                    Text(
                        text = stringResource(R.string.finished_title),
                        style = if (landscape) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.finished_total_time, state.finishedTotal),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            state.summaries.forEach { summary ->
                if (state.summaries.size > 1) {
                    item(key = "set-${summary.set}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.finished_set_header, summary.set),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = summary.total,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(summary.laps, key = { "lap-${it.set}-${it.number}" }) { lap ->
                    LapRow(lap = lap, showSet = false)
                }
            }
        }
        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.finished_exit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
