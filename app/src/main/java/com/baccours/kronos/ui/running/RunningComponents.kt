package com.baccours.kronos.ui.running

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.baccours.kronos.R
import com.baccours.kronos.format.caps
import com.baccours.kronos.format.formatDuration
import com.baccours.kronos.format.phaseLabel
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerStatus

private val SegmentBarHeight = 12.dp
private val OverallBarHeight = 6.dp
private val MaxTimeFontSize = 160.dp
private val MinTimeFontSize = 24.sp

private const val TrackAlpha = 0.18f
/** Alpha value ensuring >=4.5:1 text contrast across phase colors. */
private const val SecondaryTextAlpha = 0.85f

/** Displays set and round indicators. Hidden when total count is 1. */
@Composable
internal fun SetRoundStats(state: RunningUiState, compact: Boolean) {
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
            text = label.caps(),
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
internal fun NowGroup(
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
        stringResource(R.string.running_paused).caps()
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

/** Monospace readout, auto-sized to the largest font that fits its bounds on one line. */
@Composable
private fun TimerReadout(text: String, modifier: Modifier = Modifier) {
    val maxFontSize = with(LocalDensity.current) { MaxTimeFontSize.toSp() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            autoSize = TextAutoSize.StepBased(minFontSize = MinTimeFontSize, maxFontSize = maxFontSize),
            style = MaterialTheme.typography.displayLarge.copy(
                lineHeight = 1.1.em,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp
            ),
            maxLines = 1,
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
internal fun OverallProgress(state: RunningUiState) {
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
