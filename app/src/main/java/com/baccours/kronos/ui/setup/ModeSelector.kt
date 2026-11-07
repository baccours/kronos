package com.baccours.kronos.ui.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baccours.kronos.R
import com.baccours.kronos.model.TimerMode

/** Timer / For Time selector; the selected segment is widened. */
@Composable
internal fun ModeSelector(mode: TimerMode, onModeChange: (TimerMode) -> Unit) {
    val isTimerMode = mode == TimerMode.TIMER
    val timerWgt by animateFloatAsState(targetValue = if (isTimerMode) 2f else 1f, label = "TW")
    val forTimeWgt by animateFloatAsState(targetValue = if (!isTimerMode) 2f else 1f, label = "FTW")

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
        space = 2.dp
    ) {
        val modes = listOf(
            TimerMode.TIMER to stringResource(R.string.mode_timer),
            TimerMode.FOR_TIME to stringResource(R.string.mode_for_time)
        )

        modes.forEachIndexed { index, (itemMode, labelText) ->
            val selected = mode == itemMode
            SegmentedButton(
                selected = selected,
                onClick = { onModeChange(itemMode) },
                modifier = Modifier
                    .weight(if (itemMode == TimerMode.TIMER) timerWgt else forTimeWgt)
                    .height(56.dp),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(0.dp, Color.Transparent),
                icon = {},
                label = {
                    val upperText = labelText.uppercase()
                    val baseFontSize = if (selected) 20.sp else 12.sp
                    val leadingFontSize = if (selected) 26.sp else 16.sp
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = leadingFontSize)) {
                            append(upperText.take(1))
                        }
                        withStyle(style = SpanStyle(fontSize = baseFontSize)) {
                            append(upperText.drop(1))
                        }
                    }
                    Text(
                        text = annotatedString,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
