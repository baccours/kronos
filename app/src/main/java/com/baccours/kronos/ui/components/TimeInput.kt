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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import java.util.Locale

private const val MAX_SECONDS = (99 * 60) + 59
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

@Composable
fun TimeInput(
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showRedOnZero: Boolean = true,
    placeholder: String? = null,
    enabled: Boolean = true
) {
    // rememberSaveable: the keypad survives screen rotation
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // Digits typed in the keypad ("" = nothing typed yet: show the saved value).
    // Owned here (not by the keypad) so they are shown live in the field, and so they can be
    // committed when the sheet is dismissed. Saved: they survive screen rotation too.
    var digits by rememberSaveable { mutableStateOf("") }

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

    /** Closes the sheet; [seconds] = null leaves the saved value untouched. */
    fun closeSheet(seconds: Int?) {
        seconds?.let(onDurationChange)
        digits = "" // same snapshot as the save: no stale frame
        showSheet = false
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .width(96.dp)
            .height(48.dp)
            .revealAboveKeypad(showSheet)
            .border(
                width = 1.dp,
                color = if (showSheet) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { a11yDescription?.let { contentDescription = it } }
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.edit_value),
                role = Role.Button
            ) { showSheet = true },
        contentAlignment = Alignment.Center
    ) {
        val color = if (showRedOnZero && isZero) {
            MaterialTheme.colorScheme.error
        } else if (!hasValue && placeholder != null) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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

    if (showSheet) {
        KeypadSheet(
            digits = digits,
            onDigitsChange = { digits = it },
            maxDigits = MAX_DIGITS,
            // 4 digits = complete mmss: validate and dismiss
            onSubmit = { closeSheet(mmssToSeconds(it)) },
            // Scrim / swipe / back = "focus lost": validate what was typed (nothing typed
            // = no change; "C" then dismiss therefore acts as a cancel)
            onDismiss = {
                closeSheet(if (digits.isEmpty()) null else mmssToSeconds(digits))
            }
        ) { keyHeight ->
            // "min" (digits = plain minutes) and OK (digits = mmss)
            KeypadAction(
                label = stringResource(R.string.unit_min),
                onClick = { closeSheet(minutesToSeconds(digits)) },
                keyHeight = keyHeight,
                enabled = digits.isNotEmpty()
            )
            KeypadAction(
                label = stringResource(R.string.keypad_ok),
                onClick = { closeSheet(mmssToSeconds(digits)) },
                keyHeight = keyHeight,
                enabled = digits.isNotEmpty(),
                primary = true
            )
        }
    }
}