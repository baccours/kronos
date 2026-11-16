package com.baccours.kronos.ui.running

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.format.caps
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.icons.Done
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Pause
import com.baccours.kronos.ui.icons.Play
import com.baccours.kronos.ui.icons.SkipNext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val HOLD_MS = 600
private val RingWidth = 4.dp

/** Action controls for running workout screen. */
@Composable
internal fun Controls(
    state: RunningUiState,
    colors: PhaseColors,
    compact: Boolean,
    actions: RunningActions
) {
    val bigSize = if (compact) 64.dp else 80.dp
    val smallSize = if (compact) 56.dp else 64.dp
    val canLap = state.canLap
    // For Time: skipping a work segment finishes the set.
    val (descriptionRes, hintRes) = when {
        !canLap -> R.string.action_skip to R.string.hint_hold_skip
        state.isLastSet -> R.string.action_finish to R.string.hint_hold_finish
        else -> R.string.action_finish_set to R.string.hint_hold_finish_set
    }
    val skipDescription = stringResource(descriptionRes)
    val skipHint = stringResource(hintRes)
    val context = LocalContext.current
    var toast by remember { mutableStateOf<Toast?>(null) }

    val onEarlyRelease = remember(context, skipHint) {
        {
            toast?.cancel()
            toast = Toast.makeText(context, skipHint, Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (canLap) {
            Button(
                onClick = actions.onLap,
                enabled = !state.isPaused,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(smallSize),
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
                    text = stringResource(R.string.action_round).caps(),
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
                onClick = if (state.isPaused) actions.onResume else actions.onPause,
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
            // Irreversible, next to Pause: needs a deliberate hold.
            HoldButton(
                icon = if (canLap) Icons.Done else Icons.SkipNext,
                description = skipDescription,
                content = colors.content,
                buttonSize = smallSize,
                onHold = actions.onSkip,
                onEarlyRelease = onEarlyRelease
            )
        }
    }
}

/**
 * Round button that fires [onHold] after [HOLD_MS] of continuous press, shown by a ring filling
 * around it. Releasing earlier calls [onEarlyRelease] (a hint). Screen readers get a plain click.
 */
@Composable
private fun HoldButton(
    icon: ImageVector,
    description: String,
    content: Color,
    buttonSize: Dp,
    onHold: () -> Unit,
    onEarlyRelease: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnEarlyRelease by rememberUpdatedState(onEarlyRelease)

    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(content.copy(alpha = 0.15f))
            .drawWithContent {
                drawContent()
                val stroke = RingWidth.toPx()
                drawArc(
                    color = content,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    coroutineScope {
                        val hold = launch {
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnHold()
                        }
                        val released = tryAwaitRelease()
                        if (hold.isActive) {
                            hold.cancel()
                            if (released) currentOnEarlyRelease()
                        }
                    }
                    progress.snapTo(0f)
                })
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = description) {
                    currentOnHold()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = content, modifier = Modifier.size(32.dp))
    }
}
