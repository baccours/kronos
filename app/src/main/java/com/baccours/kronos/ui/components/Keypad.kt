package com.baccours.kronos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.ui.icons.Backspace
import com.baccours.kronos.ui.icons.Icons

// Height threshold for compact key sizing
private const val COMPACT_HEIGHT_DP = 480

private val KeyHeight = 56.dp
private val CompactKeyHeight = 40.dp
private val KeyGap = 8.dp
private val MaxKeypadWidth = 400.dp

/** Scroll margin when bringing active field into view. */
private val RevealMargin = 16.dp

/** CompositionLocal for screen keypad state. */
val LocalKeypad = staticCompositionLocalOf<KeypadState?> { null }

/** Saver preserving active field ID and typed digits across config changes. */
private val KeypadStateSaver = listSaver<KeypadState, String>(
    save = { listOf(it.activeId.orEmpty(), it.digits) },
    restore = { KeypadState(activeId = it[0].ifEmpty { null }, digits = it[1]) }
)

@Composable
fun rememberKeypadState(): KeypadState {
    val keypad = rememberSaveable(saver = KeypadStateSaver) { KeypadState() }

    // Closes keypad if active field is no longer composed after rotation.
    LaunchedEffect(keypad.activeId) {
        if (keypad.activeId != null) {
            repeat(3) { withFrameNanos { } }
            if (keypad.activeField == null) keypad.cancel()
        }
    }
    return keypad
}

/** Scrolls element into view when active. */
@Composable
internal fun Modifier.revealWhenActive(active: Boolean): Modifier {
    // Re-measured on layout
    val requester = remember { BringIntoViewRequester() }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { RevealMargin.toPx() }

    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { } // Await layout adjustment before scrolling
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

/**
 * Binds a field to the keypad: tap to edit, outlined while active, re-attached after
 * recomposition or rotation, and the edit is canceled if [enabled] turns off.
 */
@Composable
internal fun Modifier.keypadField(
    keypad: KeypadState?,
    field: KeypadField,
    enabled: Boolean = true
): Modifier {
    val active = keypad?.activeId == field.id
    SideEffect { if (active) keypad.attach(field) }
    LaunchedEffect(enabled, active) { if (active && !enabled) keypad.cancel() }
    return revealWhenActive(active)
        .border(
            width = 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = MaterialTheme.shapes.extraSmall
        )
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clickable(
            enabled = enabled,
            onClickLabel = stringResource(R.string.edit_value),
            role = Role.Button
        ) { keypad?.open(field) }
}

/** Digits typed into field [id]; empty unless it is the active one. */
internal fun KeypadState?.digitsFor(id: String): String =
    this?.takeIf { it.activeId == id }?.digits.orEmpty()

/** Docked numeric keypad UI. */
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
                            // "N min" commits the typed digits as minutes; OK reads them as mmss
                            val minutes = state.digits.toIntOrNull()
                            KeypadAction(
                                label = if (minutes != null) {
                                    stringResource(R.string.keypad_n_min, minutes)
                                } else {
                                    stringResource(R.string.unit_min)
                                },
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
                            // Empty spacer aligning OK button
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

/** Action key spanning [rows] rows in keypad action column. */
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

/** Spacer for aligning action keys in right-hand keypad column. */
@Composable
private fun KeypadActionSpacer(keyHeight: Dp, rows: Int = 2) {
    Spacer(modifier = Modifier.height(keyHeight * rows + KeyGap * (rows - 1)))
}

/** Numeric 3x4 key grid. */
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
