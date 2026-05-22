package com.baccours.kronos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

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
            onClick = { if (value > range.first) onValueChange(value - 1) },
            enabled = value > range.first
        ) {
            Icon(imageVector = Icons.Remove, contentDescription = "Decrease")
        }

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp)
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
                        if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                            editValue = newValue
                            if (newValue.length == 2) focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus) {
                                editValue.toIntOrNull()?.let { parsed ->
                                    if (parsed in range) onValueChange(parsed)
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
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Text(
                    text = value.toString(),
                    style = textStyle
                )
            }
        }

        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            enabled = value < range.last
        ) {
            Icon(imageVector = Icons.Add, contentDescription = "Increase")
        }
    }
}
