package com.baccours.kronos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun TimeInput(
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    showRedOnZero: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val displayMinutes = durationSeconds / 60
    val displaySeconds = durationSeconds % 60
    val displayString = "%02d:%02d".format(displayMinutes, displaySeconds)

    Box(
        modifier = modifier
            .clickable {
                isEditing = true
                editValue = ""
            },
        contentAlignment = Alignment.Center
    ) {
        if (isEditing) {
            BasicTextField(
                value = editValue,
                onValueChange = { newValue ->
                    if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                        editValue = newValue
                        if (newValue.length == 4) {
                            val mins = newValue.substring(0, 2).toIntOrNull() ?: 0
                            val secs = newValue.substring(2, 4).toIntOrNull() ?: 0
                            onDurationChange(mins * 60 + secs)
                            isEditing = false
                        }
                    }
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (!it.isFocused && isEditing) {
                            if (editValue.isNotEmpty()) {
                                // Pad with leading zeros if less than 4 digits
                                val padded = editValue.padStart(4, '0')
                                val mins = padded.substring(0, 2).toIntOrNull() ?: 0
                                val secs = padded.substring(2, 4).toIntOrNull() ?: 0
                                onDurationChange(mins * 60 + secs)
                            }
                            isEditing = false
                        }
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            val color = if (showRedOnZero && durationSeconds == 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = displayString,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
