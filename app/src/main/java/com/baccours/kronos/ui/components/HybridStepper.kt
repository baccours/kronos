package com.baccours.kronos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.ui.icons.Add
import com.baccours.kronos.ui.icons.Icons
import com.baccours.kronos.ui.icons.Remove

/**
 * Stepper component combining -/+ buttons with direct keypad entry.
 *
 * @param fieldId Unique, stable identifier for keypad binding.
 */
@Composable
fun HybridStepper(
    fieldId: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier
) {
    val keypad = LocalKeypad.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    val field = remember(fieldId, range) {
        KeypadField(
            id = fieldId,
            kind = KeypadKind.NUMBER,
            // Auto-commits when maximum digit count is reached
            maxDigits = range.last.toString().length,
            // Ignores out-of-range values
            apply = { digits, _ -> digits.toIntOrNull()?.takeIf { it in range }?.let(currentOnChange) },
            accepts = { digits -> digits.toIntOrNull()?.let { it in range } == true }
        )
    }
    val active = keypad?.activeId == fieldId

    // Re-attaches active field to keypad
    SideEffect { if (active) keypad.attach(field) }

    // Digits typed on keypad; displays current value if empty
    val digits = if (active) keypad.digits else ""

    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                keypad?.discardDigits(fieldId) // Discard typed digits on manual stepper change
                if (value > range.first) onValueChange(value - 1)
            },
            enabled = value > range.first
        ) {
            Icon(imageVector = Icons.Remove, contentDescription = stringResource(R.string.stepper_decrease))
        }

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp)
                .revealWhenActive(active)
                .border(
                    width = 1.dp,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.extraSmall
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.edit_value),
                    role = Role.Button
                ) { keypad?.open(field) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digits.ifEmpty { value.toString() },
                style = textStyle
            )
        }

        IconButton(
            onClick = {
                keypad?.discardDigits(fieldId)
                if (value < range.last) onValueChange(value + 1)
            },
            enabled = value < range.last
        ) {
            Icon(imageVector = Icons.Add, contentDescription = stringResource(R.string.stepper_increase))
        }
    }
}
