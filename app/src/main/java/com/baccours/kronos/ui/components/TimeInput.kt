package com.baccours.kronos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
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

/** Converts mmss digit string to total seconds. */
private fun mmssToSeconds(digits: String): Int {
    if (digits.isEmpty()) return 0
    val padded = digits.padStart(MAX_DIGITS, '0')
    val mins = padded.substring(0, 2).toIntOrNull() ?: 0
    val secs = padded.substring(2, 4).toIntOrNull() ?: 0
    return (mins * 60 + secs).coerceAtMost(MAX_SECONDS)
}

/** Converts minute digit string to total seconds. */
private fun minutesToSeconds(digits: String): Int {
    val mins = digits.toIntOrNull() ?: return 0
    return (mins * 60).coerceAtMost(MAX_SECONDS)
}

/**
 * Editable time duration input displaying formatted mm:ss.
 *
 * @param fieldId Unique, stable identifier for keypad binding.
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

    // Re-attaches field to keypad
    SideEffect { if (active) keypad.attach(field) }
    // Cancels active edit if field is disabled
    LaunchedEffect(enabled, active) { if (active && !enabled) keypad.cancel() }

    // Digits typed on keypad; displays saved value if empty
    val digits = if (active) keypad.digits else ""

    // Displays padded mm:ss without immediate conversion
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
            .defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
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
            // High contrast placeholder color
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = displayString,
            style = textStyle,
            color = color
        )
    }
}
