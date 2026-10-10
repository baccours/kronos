package com.baccours.kronos.ui.components

import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.baccours.kronos.R
import com.baccours.kronos.ui.icons.Backspace
import com.baccours.kronos.ui.icons.Icons
import java.util.Locale

private const val MAX_SECONDS = (99 * 60) + 59
private const val MAX_DIGITS = 4

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

@OptIn(ExperimentalFoundationApi::class)
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

    // Used to keep this field visible above the keypad sheet.
    // Not saved: re-measured after a rotation.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val keypadInset = LocalKeypadInset.current

    // Digits being typed in the keypad (null = nothing typed yet: show the saved value).
    // They are shown as typed, padded to mm:ss, with NO conversion to seconds
    // Not saved: the keypad re-reports it after a rotation.
    var previewDigits by remember { mutableStateOf<String?>(null) }

    val typed = previewDigits?.padStart(MAX_DIGITS, '0')
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
            .width(96.dp)
            .height(48.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { fieldSize = it }
            .border(
                width = 1.dp,
                color = if (showSheet) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .semantics { a11yDescription?.let { contentDescription = it } },
            style = textStyle,
            color = color
        )
    }

    // The sheet is a separate window, so the list doesn't know it hides part of the screen.
    // Once the sheet has stopped moving, ask the list to reveal the field plus the part of
    // the list covered by the sheet: the field ends up right above the keypad.
    // (The list gets its extra bottom padding from keypadInset, so it can scroll that far.)
    LaunchedEffect(showSheet) {
        if (showSheet) {
            keypadInset?.awaitSettled()
            withFrameNanos { } // let the list apply its final bottom padding
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = fieldSize.width.toFloat(),
                    bottom = fieldSize.height + (keypadInset?.overlapPx ?: 0f)
                )
            )
        }
    }

    if (showSheet) {
        // Lives exactly as long as the sheet: whatever closes it (OK, min, swipe, scrim,
        // or this field leaving the composition), the list's padding goes back to 0.
        DisposableEffect(Unit) {
            onDispose {
                keypadInset?.sheetTopPx = Float.NaN
                previewDigits = null
            }
        }

        DurationKeypadSheet(
            onTopChange = { keypadInset?.sheetTopPx = it },
            onPreviewChange = { previewDigits = it },
            onConfirm = {
                previewDigits = null // same snapshot as the save: no stale frame
                onDurationChange(it)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationKeypadSheet(
    onTopChange: (Float) -> Unit,
    onPreviewChange: (String?) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 400.dp,
        // Lighter scrim so the field being edited stays readable
        scrimColor = Color.Black.copy(alpha = 0.12f),
        dragHandle = {
            // The drag handle is the very first thing in the sheet: its top is the sheet's top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onTopChange(it.screenTop(view)) }
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
        DurationKeypad(
            onPreviewChange = onPreviewChange,
            onConfirm = onConfirm,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun DurationKeypad(
    onPreviewChange: (String?) -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // rememberSaveable: typed digits survive screen rotation too
    var digits by rememberSaveable { mutableStateOf("") }

    val hasDigits = digits.isNotEmpty()
    val screenHeightDp = LocalWindowInfo.current.containerDpSize.height
    val keyHeight = if (screenHeightDp < COMPACT_HEIGHT_DP.dp) CompactKeyHeight else KeyHeight

    // Report the typed digits as they are: the TimeInput behind the keypad displays them.
    LaunchedEffect(digits) {
        onPreviewChange(digits.ifEmpty { null })
    }

    fun appendDigit(c: Char) {
        if (digits.length >= MAX_DIGITS) return
        val newDigits = digits + c
        digits = newDigits
        // 4 digits = complete mmss: validate and dismiss
        if (newDigits.length == MAX_DIGITS) onConfirm(mmssToSeconds(newDigits))
    }

    val minLabel = stringResource(R.string.unit_min)
    val okLabel = stringResource(R.string.keypad_ok)

    // Scrollable as a safety net on very small screens
    Row(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KeyGap)
    ) {
        DigitPad(
            keyHeight = keyHeight,
            onDigit = { appendDigit(it) },
            onDelete = { digits = digits.dropLast(1) },
            onClear = { digits = "" },
            modifier = Modifier.weight(3f)
        )
        // "min" (digits = plain minutes) and OK (digits = mmss)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            FilledTonalButton(
                onClick = { onConfirm(minutesToSeconds(digits)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight * 2 + KeyGap),
                enabled = hasDigits,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(minLabel, style = MaterialTheme.typography.titleLarge)
            }
            Button(
                onClick = { onConfirm(mmssToSeconds(digits)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight * 2 + KeyGap),
                enabled = hasDigits,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(okLabel, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
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
        if (icon != null)
            Icon(imageVector = icon, contentDescription = null)
        else if (label != null)
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = textColor
            )
    }
}
