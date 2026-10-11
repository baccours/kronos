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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

@Composable
fun HybridStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..99
) {
    // rememberSaveable: the keypad survives screen rotation
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // Digits typed in the keypad ("" = nothing typed yet: show the current value)
    var digits by rememberSaveable { mutableStateOf("") }

    // Auto-validate as soon as the longest possible value has been typed (99 -> 2 digits)
    val maxDigits = range.last.toString().length

    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    /** Closes the sheet; a null or out-of-range [newValue] leaves the value untouched. */
    fun closeSheet(newValue: Int?) {
        newValue?.takeIf { it in range }?.let(onValueChange)
        digits = ""
        showSheet = false
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) },
            enabled = value > range.first
        ) {
            Icon(imageVector = Icons.Remove, contentDescription = stringResource(R.string.stepper_decrease))
        }

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp)
                .revealAboveKeypad(showSheet)
                .border(
                    width = 1.dp,
                    color = if (showSheet) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.extraSmall
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.edit_value),
                    role = Role.Button
                ) { showSheet = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digits.ifEmpty { value.toString() },
                style = textStyle
            )
        }

        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            enabled = value < range.last
        ) {
            Icon(imageVector = Icons.Add, contentDescription = stringResource(R.string.stepper_increase))
        }
    }

    if (showSheet) {
        KeypadSheet(
            digits = digits,
            onDigitsChange = { digits = it },
            maxDigits = maxDigits,
            onSubmit = { closeSheet(it.toIntOrNull()) },
            // Scrim / swipe / back = "focus lost": validate what was typed (nothing typed
            // or out of range = no change, same as the previous text field)
            onDismiss = { closeSheet(digits.toIntOrNull()) }
        ) { keyHeight ->
            // No "min" key here: leave its place empty so OK stays on the 2 bottom rows
            KeypadActionSpacer(keyHeight)
            KeypadAction(
                label = stringResource(R.string.keypad_ok),
                onClick = { closeSheet(digits.toIntOrNull()) },
                keyHeight = keyHeight,
                enabled = digits.toIntOrNull()?.let { it in range } == true,
                primary = true
            )
        }
    }
}