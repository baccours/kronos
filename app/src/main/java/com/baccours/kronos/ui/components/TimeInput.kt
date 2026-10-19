package com.baccours.kronos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.model.TimerConfig
import java.util.Locale

private const val MAX_SECONDS = TimerConfig.MAX_DURATION_SECONDS
private const val MAX_DIGITS = 4

/**
 * Digits are read as mmss, padded with leading zeros:
 * "5" -> 00:05, "45" -> 00:45, "130" -> 01:30, "1030" -> 10:30
 */
private fun mmssToSeconds(digits: String): Int {
    if (digits.isEmpty()) return 0
    val padded = digits.padStart(MAX_DIGITS, '0')
    val mins = padded.substring(0, 2).toIntOrNull() ?: 0
    val secs = padded.substring(2, 4).toIntOrNull() ?: 0
    return (mins * 60 + secs).coerceAtMost(MAX_SECONDS)
}

/** Digits are read as a plain number of minutes: "2" -> 02:00, "15" -> 15:00 */
private fun minutesToSeconds(digits: String): Int {
    val mins = digits.toIntOrNull() ?: return 0
    return (mins * 60).coerceAtMost(MAX_SECONDS)
}

/**
 * A duration shown as mm:ss. Tapping it edits it with the docked keypad of the screen
 * ([LocalKeypad]); what is typed is shown live in the field and applied when the edit ends.
 *
 * @param fieldId identifies this field to the keypad. Must be unique on the screen and stable
 *   across recompositions and rotation.
 */
@Composable
fun TimeInput(
    fieldId: String,
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showRedOnZero: Boolean = true,
    placeholder: String? = null,
    enabled: Boolean = true
) {
    val keypad = LocalKeypad.current
    val currentOnChange by rememberUpdatedState(onDurationChange)
    val field = remember(fieldId) {
        KeypadField(
            id = fieldId,
            kind = KeypadKind.TIME,
            maxDigits = MAX_DIGITS,
            apply = { digits, asMinutes ->
                currentOnChange(if (asMinutes) minutesToSeconds(digits) else mmssToSeconds(digits))
            },
            accepts = { true }
        )
    }
    val active = keypad?.activeId == fieldId

    // Hands the keypad the current version of this field (e.g. after a rotation).
    SideEffect { if (active) keypad?.attach(field) }
    // A field that gets disabled while it is being edited (e.g. rest with a single set) stops the edit.
    LaunchedEffect(enabled, active) { if (active && !enabled) keypad?.cancel() }

    // Digits typed on the keypad ("" = nothing typed yet: show the saved value).
    val digits = if (active) keypad?.digits.orEmpty() else ""

    // Shown as typed, padded to mm:ss, with NO conversion to seconds
    val typed = digits.ifEmpty { null }?.padStart(MAX_DIGITS, '0')
    val displayMinutes = typed?.substring(0, 2)?.toIntOrNull() ?: (durationSeconds / 60)
    val displaySeconds = typed?.substring(2, 4)?.toIntOrNull() ?: (durationSeconds % 60)
    val hasValue = typed != null || durationSeconds > 0
    val isZero = displayMinutes == 0 && displaySeconds == 0

    val displayString = when {
        hasValue -> "%02d:%02d".format(Locale.ROOT, displayMinutes, displaySeconds)
        placeholder != null -> placeholder
        else -> "00:00"
    }
    val a11yDescription = if (hasValue) {
        stringResource(R.string.duration_a11y, displayMinutes, displaySeconds)
    } else null

    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .width(96.dp)
            .height(48.dp)
            .revealWhenActive(active)
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { a11yDescription?.let { contentDescription = it } }
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.edit_value),
                role = Role.Button
            ) { keypad?.open(field) },
        contentAlignment = Alignment.Center
    ) {
        val color = if (showRedOnZero && isZero) {
            MaterialTheme.colorScheme.error
        } else if (!hasValue && placeholder != null) {
            // 0.75 keeps the "no cap" hint at 4.5:1 or more (0.5 was 2.5:1 in the light theme)
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = displayString,
            modifier = Modifier.fillMaxWidth(),
            style = textStyle,
            color = color
        )
    }
}
