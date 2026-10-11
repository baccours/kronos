package com.baccours.kronos.ui.components

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
private val CompactKeyHeight = 48.dp
private val KeyGap = 8.dp

// ---------------------------------------------------------------------------------------
// Keypad inset: lets a list make room for the keypad sheet (like imePadding() for the
// keyboard). Usage, in the screen that contains the list:
//
//     val keypadInset = remember { KeypadInsetState() }
//     CompositionLocalProvider(LocalKeypadInset provides keypadInset) {
//         LazyColumn(
//             modifier = Modifier.trackKeypadInset(keypadInset),
//             contentPadding = PaddingValues(bottom = keypadInset.bottomPadding())
//         ) { ... }
//     }
// ---------------------------------------------------------------------------------------

@Stable
class KeypadInsetState {
    /** Top edge of the keypad sheet on screen, in px. NaN while no keypad is open. */
    internal var sheetTopPx by mutableFloatStateOf(Float.NaN)

    /** Bottom edge of the list on screen, in px. NaN until the list has been measured. */
    internal var listBottomPx by mutableFloatStateOf(Float.NaN)

    /** Part of the list currently hidden by the keypad, in px (0 when closed). */
    val overlapPx: Float
        get() = if (sheetTopPx.isNaN() || listBottomPx.isNaN()) {
            0f
        } else {
            (listBottomPx - sheetTopPx).coerceAtLeast(0f)
        }

    /** [overlapPx] as Dp: use it as the bottom content padding of the list. */
    @Composable
    fun bottomPadding(): Dp = with(LocalDensity.current) { overlapPx.toDp() }
}

val LocalKeypadInset = staticCompositionLocalOf<KeypadInsetState?> { null }

/** Apply to the list: tells the state where the list ends on screen. */
@Composable
fun Modifier.trackKeypadInset(state: KeypadInsetState): Modifier {
    val view = LocalView.current
    return this.onGloballyPositioned { coordinates ->
        state.listBottomPx = coordinates.screenTop(view) + coordinates.size.height
    }
}

/** Y position on the physical screen (the sheet lives in another window than the list). */
private fun LayoutCoordinates.screenTop(view: View): Float {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    return location[1] + positionInRoot().y
}

/** Suspends until the sheet has stopped moving (end of its slide-in animation). */
private suspend fun KeypadInsetState.awaitSettled() {
    var last = Float.NaN
    var stableFrames = 0
    repeat(120) { // safety net: ~2 seconds
        withFrameNanos { }
        val current = sheetTopPx
        if (!current.isNaN() && current == last) {
            stableFrames++
            if (stableFrames >= 2) return
        } else {
            stableFrames = 0
        }
        last = current
    }
}

/**
 * Apply to the field that opens a keypad sheet. Once the sheet has stopped moving, asks the
 * enclosing list to reveal the field plus the part of the list covered by the sheet: the
 * field ends up right above the keypad.
 * (The list gets its extra bottom padding from [KeypadInsetState], so it can scroll that far.)
 */
@Composable
internal fun Modifier.revealAboveKeypad(sheetOpen: Boolean): Modifier {
    val keypadInset = LocalKeypadInset.current
    // Not saved: re-measured after a rotation.
    val requester = remember { BringIntoViewRequester() }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(sheetOpen) {
        if (sheetOpen) {
            keypadInset?.awaitSettled()
            withFrameNanos { } // let the list apply its final bottom padding
            requester.bringIntoView(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = fieldSize.width.toFloat(),
                    bottom = fieldSize.height + (keypadInset?.overlapPx ?: 0f)
                )
            )
        }
    }

    return this
        .bringIntoViewRequester(requester)
        .onSizeChanged { fieldSize = it }
}

/**
 * Bottom sheet with a digit pad. Stateless: the caller owns [digits] (typically
 * rememberSaveable) so it can show them live in its field AND commit them on dismissal.
 *
 * @param onSubmit called with the final digits when [maxDigits] digits have been typed.
 * @param onDismiss called when the user dismisses the sheet (scrim, swipe, back), NOT when
 *   the caller closes it itself. This is the "focus lost" moment: commit the digits here.
 * @param actions right-hand column. Use [KeypadAction] to fill it; the receiver gives the
 *   height of one key so actions can span a given number of rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeypadSheet(
    digits: String,
    onDigitsChange: (String) -> Unit,
    maxDigits: Int,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.(keyHeight: Dp) -> Unit
) {
    val view = LocalView.current
    val keypadInset = LocalKeypadInset.current

    // The sheet may invoke its callback from a long-lived gesture/animation scope:
    // always call the latest lambda so it sees the latest digits.
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // Lives exactly as long as the sheet: whatever closes it (OK, min, swipe, scrim,
    // or the field leaving the composition), the list's padding goes back to 0.
    // NOTE: never commit anything here, this also runs on rotation / navigation.
    DisposableEffect(Unit) {
        onDispose { keypadInset?.sheetTopPx = Float.NaN }
    }

    ModalBottomSheet(
        onDismissRequest = { currentOnDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 400.dp,
        // Lighter scrim so the field being edited stays readable
        scrimColor = Color.Black.copy(alpha = 0.12f),
        dragHandle = {
            // The drag handle is the very first thing in the sheet: its top is the sheet's top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { keypadInset?.sheetTopPx = it.screenTop(view) }
                    .padding(top = 8.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.extraLarge
                ) {}
            }
        }
    ) {
        Keypad(
            digits = digits,
            onDigitsChange = onDigitsChange,
            maxDigits = maxDigits,
            onSubmit = onSubmit,
            actions = actions,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun Keypad(
    digits: String,
    onDigitsChange: (String) -> Unit,
    maxDigits: Int,
    onSubmit: (String) -> Unit,
    actions: @Composable ColumnScope.(keyHeight: Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenHeightDp = LocalWindowInfo.current.containerDpSize.height
    val keyHeight = if (screenHeightDp < COMPACT_HEIGHT_DP.dp) CompactKeyHeight else KeyHeight

    fun appendDigit(c: Char) {
        if (digits.length >= maxDigits) return
        val newDigits = digits + c
        onDigitsChange(newDigits)
        // Complete entry: validate and dismiss
        if (newDigits.length == maxDigits) onSubmit(newDigits)
    }

    // Scrollable as a safety net on very small screens
    Row(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KeyGap)
    ) {
        DigitPad(
            keyHeight = keyHeight,
            onDigit = { appendDigit(it) },
            onDelete = { onDigitsChange(digits.dropLast(1)) },
            onClear = { onDigitsChange("") },
            modifier = Modifier.weight(3f)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            actions(this, keyHeight)
        }
    }
}

/**
 * Button for the keypad's right-hand column, [rows] keys tall (the pad has 4 rows).
 * [primary] = filled button (OK), otherwise tonal (e.g. "min").
 */
@Composable
internal fun KeypadAction(
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
internal fun KeypadActionSpacer(keyHeight: Dp, rows: Int = 2) {
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