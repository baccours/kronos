package com.baccours.kronos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TimeInput(
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showRedOnZero: Boolean = true,
    placeholder: String? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val maxSeconds = (99 * 60) + 59

    val displayMinutes = durationSeconds / 60
    val displaySeconds = durationSeconds % 60
    val displayString = when {
        durationSeconds > 0 -> "%02d:%02d".format(displayMinutes, displaySeconds)
        placeholder != null -> placeholder
        else -> "00:00"
    }

    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
    )

    Box(
        modifier = modifier
            .width(96.dp)
            .border(
                width = 1.dp,
                color = if (isEditing) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable {
                isEditing = true
                editValue = ""
            },
        contentAlignment = Alignment.Center
    ) {
        if (isEditing) {
            var hasGainedFocus by remember { mutableStateOf(false) }
            
            BasicTextField(
                value = editValue,
                onValueChange = { newValue ->
                    if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                        editValue = newValue
                        if (newValue.length == 4) focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hasGainedFocus = true
                        } else if (hasGainedFocus) {
                            if (editValue.isNotEmpty()) {
                                // Pad with leading zeros if less than 4 digits
                                val padded = editValue.padStart(4, '0')
                                val mins = padded.substring(0, 2).toIntOrNull() ?: 0
                                val secs = padded.substring(2, 4).toIntOrNull() ?: 0
                                val totalSeconds = (mins * 60 + secs).coerceAtMost(maxSeconds)
                                onDurationChange(totalSeconds)
                            }
                            isEditing = false
                        }
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            val color = if (showRedOnZero && durationSeconds == 0) {
                MaterialTheme.colorScheme.error
            } else if (durationSeconds == 0 && placeholder != null) {
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
    }
}
