package com.baccours.kronos.ui.setup

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.baccours.kronos.model.TimerMode
import kotlin.math.abs

/** Horizontal swipe switches mode: right = TIMER, left = FOR_TIME. Screen edges are left to back gestures. */
@Composable
internal fun Modifier.swipeToSwitchMode(
    mode: TimerMode,
    onModeChange: (TimerMode) -> Unit
): Modifier {
    // Read the latest values from inside the long-lived gesture block.
    val currentMode by rememberUpdatedState(mode)
    val currentOnModeChange by rememberUpdatedState(onModeChange)

    return pointerInput(Unit) {
        val threshold = 48.dp.toPx()
        val slop = viewConfiguration.touchSlop
        val edge = 32.dp.toPx() // Exclude edge back-gesture areas
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < edge || down.position.x > size.width - edge) {
                return@awaitEachGesture
            }
            var dx = 0f
            var dy = 0f
            var claimed = false
            var done = false
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                // Abort on multitouch or consumed gesture
                if (event.changes.size > 1 || (!claimed && change.isConsumed)) break

                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y

                // Consume horizontal swipe gestures
                if (!claimed && abs(dx) > slop && abs(dx) > 2 * abs(dy)) {
                    claimed = true
                }

                if (claimed) {
                    change.consume()
                    if (!done && abs(dx) > threshold) {
                        done = true
                        val target = if (dx > 0) TimerMode.TIMER else TimerMode.FOR_TIME
                        if (target != currentMode) currentOnModeChange(target)
                    }
                }
            } while (change.pressed)
        }
    }
}
