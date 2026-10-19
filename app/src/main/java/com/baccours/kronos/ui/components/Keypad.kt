package com.baccours.kronos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.ui.icons.Backspace
import com.baccours.kronos.ui.icons.Icons

// Below this screen height (e.g. a phone in landscape) the keypad keys are shorter
private const val COMPACT_HEIGHT_DP = 480

private val KeyHeight = 56.dp
private val CompactKeyHeight = 40.dp
private val KeyGap = 8.dp
private val MaxKeypadWidth = 400.dp

/** Room kept around a field that is scrolled into view, so it does not sit flush against an edge. */
private val RevealMargin = 16.dp

// ---------------------------------------------------------------------------------------
// The docked keypad. Usage, in the screen that has the editable fields:
//
//     val keypad = rememberKeypadState()
//     BackHandler(enabled = keypad.isOpen) { keypad.commitAndClose() }
//     Scaffold(bottomBar = { if (keypad.isOpen) DockedKeypad(keypad) else MyBottomBar() }) {
//         CompositionLocalProvider(LocalKeypad provides keypad) { ...TimeInput / HybridStepper... }
//     }
//
// The keypad takes the place of the bottom bar, so the list simply gets less room (through the
// Scaffold's content padding) and the field being edited is scrolled into view; no window
// coordinates are involved. Tapping another field commits the current one and moves on.
// ---------------------------------------------------------------------------------------

/** The keypad the fields in this part of the UI edit with; null = fields are read-only (e.g. previews). */
val LocalKeypad = staticCompositionLocalOf<KeypadState?> { null }

/** Only the active field's id and the typed digits survive a rotation; fields re-attach themselves. */
private val KeypadStateSaver = listSaver<KeypadState, String>(
    save = { listOf(it.activeId.orEmpty(), it.digits) },
    restore = { KeypadState(activeId = it[0].ifEmpty { null }, digits = it[1]) }
)

@Composable
fun rememberKeypadState(): KeypadState {
    val keypad = rememberSaveable(saver = KeypadStateSaver) { KeypadState() }

    // After a rotation the active field is restored by id only. If it is not composed any more
    // (e.g. it is scrolled out of the list) nothing can receive the digits: close instead of
    // leaving a keypad that edits nothing. Fields attach during the first frames.
    LaunchedEffect(keypad.activeId) {
        if (keypad.activeId != null) {
            repeat(3) { withFrameNanos { } }
            if (keypad.activeField == null) keypad.cancel()
        }
    }
    return keypad
}

/**
 * Apply to an editable field: when it becomes the active one, scrolls it into view (the list has
 * just lost the space the docked keypad takes).
 */
@Composable
internal fun Modifier.revealWhenActive(active: Boolean): Modifier {
    // Not saved: re-measured after a rotation.
    val requester = remember { BringIntoViewRequester() }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { RevealMargin.toPx() }

    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { } // let the list adopt the space left by the keypad first
            requester.bringIntoView(
                Rect(
                    left = 0f,
                    top = -marginPx,
                    right = fieldSize.width.toFloat(),
                    bottom = fieldSize.height + marginPx
                )
            )
        }
    }

    return this
        .bringIntoViewRequester(requester)
        .onSizeChanged { fieldSize = it }
}

/** The digit pad with its action keys, shown instead of the screen's bottom bar while a field is edited. */
@Composable
fun DockedKeypad(
    state: KeypadState,
    modifier: Modifier = Modifier
) {
    val kind = state.activeField?.kind ?: KeypadKind.TIME
    val screenHeightDp = LocalWindowInfo.current.containerDpSize.height
    val keyHeight = if (screenHeightDp < COMPACT_HEIGHT_DP.dp) CompactKeyHeight else KeyHeight

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = MaxKeypadWidth)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyGap)
            ) {
                DigitPad(
                    keyHeight = keyHeight,
                    onDigit = state::type,
                    onDelete = state::delete,
                    onClear = state::clear,
                    modifier = Modifier.weight(3f)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KeyGap)
                ) {
                    when (kind) {
                        KeypadKind.TIME -> {
                            // "min": digits are plain minutes. OK: digits are mmss (or just close).
                            KeypadAction(
                                label = stringResource(R.string.unit_min),
                                onClick = state::commitAsMinutesAndClose,
                                keyHeight = keyHeight,
                                enabled = state.digits.isNotEmpty()
                            )
                            KeypadAction(
                                label = stringResource(R.string.keypad_ok),
                                onClick = state::commitAndClose,
                                keyHeight = keyHeight,
                                primary = true
                            )
                        }
                        KeypadKind.NUMBER -> {
                            // No "min" key here: leave its place empty so OK stays on the 2 bottom rows
                            KeypadActionSpacer(keyHeight)
                            KeypadAction(
                                label = stringResource(R.string.keypad_ok),
                                onClick = state::commitAndClose,
                                keyHeight = keyHeight,
                                enabled = state.canSubmit,
                                primary = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Button for the keypad's right-hand column, [rows] keys tall (the pad has 4 rows).
 * [primary] = filled button (OK), otherwise tonal (e.g. "min").
 */
@Composable
private fun KeypadAction(
    label: String,
    onClick: () -> Unit,
    keyHeight: Dp,
    modifier: Modifier = Modifier,
    rows: Int = 2,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val actionModifier = modifier
        .fillMaxWidth()
        .height(keyHeight * rows + KeyGap * (rows - 1))

    if (primary) {
        Button(
            onClick = onClick,
            modifier = actionModifier,
            enabled = enabled,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = actionModifier,
            enabled = enabled,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * Empty space [rows] keys tall in the right-hand column, to push an action down:
 * [KeypadActionSpacer] + [KeypadAction] = an action in the 2 bottom rows.
 */
@Composable
private fun KeypadActionSpacer(keyHeight: Dp, rows: Int = 2) {
    Spacer(modifier = Modifier.height(keyHeight * rows + KeyGap * (rows - 1)))
}

/** 3 columns x 4 rows: 1-9, then backspace / 0 / clear. */
@Composable
private fun DigitPad(
    keyHeight: Dp,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deleteDescription = stringResource(R.string.keypad_delete)
    val clearDescription = stringResource(R.string.keypad_clear)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(KeyGap)
    ) {
        listOf("123", "456", "789").forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyGap)
            ) {
                rowDigits.forEach { c ->
                    Key(
                        label = c.toString(),
                        onClick = { onDigit(c) },
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            Key(
                icon = Icons.Backspace,
                description = deleteDescription,
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(keyHeight)
            )
            Key(
                label = "0",
                onClick = { onDigit('0') },
                modifier = Modifier
                    .weight(1f)
                    .height(keyHeight)
            )
            Key(
                label = "C",
                textColor = MaterialTheme.colorScheme.error,
                description = clearDescription,
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .height(keyHeight)
            )
        }
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    label: String? = null,
    textColor: Color = Color.Unspecified
) {
    val keyModifier = modifier.semantics {
        description?.let { contentDescription = it }
    }

    OutlinedButton(
        onClick = onClick,
        modifier = keyModifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        when {
            icon != null -> Icon(imageVector = icon, contentDescription = null)
            label != null -> Text(text = label, style = MaterialTheme.typography.titleLarge, color = textColor)
        }
    }
}
