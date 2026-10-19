package com.baccours.kronos.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the digits typed on the keypad mean. */
enum class KeypadKind {
    /** A duration: digits are read as mmss, or as plain minutes with the "min" key. */
    TIME,

    /** A plain whole number (sets, rounds). */
    NUMBER
}

/**
 * A field that can be edited with the docked keypad. Created once per field (see TimeInput and
 * HybridStepper) and handed to [KeypadState.open] when the field is tapped.
 */
@Stable
class KeypadField(
    /** Stable across recompositions and rotation: it is what the keypad remembers about the field. */
    val id: String,
    val kind: KeypadKind,
    /** Typing this many digits completes the entry. */
    val maxDigits: Int,
    /** Puts the typed digits into the field's value. [asMinutes]: read them as plain minutes. */
    val apply: (digits: String, asMinutes: Boolean) -> Unit,
    /** False when the digits would not be a valid value (an out-of-range number is ignored). */
    val accepts: (digits: String) -> Boolean
)

/**
 * The one docked keypad of a screen: which field is being edited and what has been typed.
 *
 * Whatever ends an edit by moving on (tapping another field, OK, "min", back, completing the
 * digits) commits what was typed; a [cancel] throws it away. Typing into a field and then tapping
 * another therefore needs no dismissing step in between.
 *
 * Only the id and the digits survive a rotation (see rememberKeypadState); the field object
 * re-attaches itself with [attach] when it is composed again.
 */
@Stable
class KeypadState(activeId: String? = null, digits: String = "") {

    var activeId: String? by mutableStateOf(activeId)
        private set

    /** What has been typed for the active field; empty = nothing typed yet (the field shows its value). */
    var digits: String by mutableStateOf(digits)
        private set

    var activeField: KeypadField? by mutableStateOf(null)
        private set

    val isOpen: Boolean get() = activeId != null

    /** OK makes sense: nothing typed (just close) or digits the field accepts. */
    val canSubmit: Boolean
        get() = digits.isEmpty() || activeField?.accepts?.invoke(digits) == true

    /** The field was tapped: commit what was typed in the previous one and edit this one. */
    fun open(field: KeypadField) {
        if (activeId == field.id) {
            activeField = field
            return
        }
        commit(asMinutes = false)
        activeId = field.id
        activeField = field
        digits = ""
    }

    /** A field composed (again, e.g. after a rotation) hands over its current self if it is the active one. */
    fun attach(field: KeypadField) {
        if (activeId == field.id) activeField = field
    }

    fun type(digit: Char) {
        val field = activeField ?: return
        if (!digit.isDigit() || digits.length >= field.maxDigits) return
        digits += digit
        if (digits.length == field.maxDigits) commitAndClose()
    }

    fun delete() {
        digits = digits.dropLast(1)
    }

    fun clear() {
        digits = ""
    }

    /** Drops what was typed for [fieldId] (its value was changed another way, e.g. with +/-). */
    fun discardDigits(fieldId: String) {
        if (activeId == fieldId) digits = ""
    }

    fun commitAndClose() {
        commit(asMinutes = false)
        close()
    }

    fun commitAsMinutesAndClose() {
        commit(asMinutes = true)
        close()
    }

    /** Closes without applying what was typed. */
    fun cancel() = close()

    /** Cancels only if [fieldId] is the one being edited (it was deleted or disabled). */
    fun cancelIf(fieldId: String) {
        if (activeId == fieldId) close()
    }

    private fun commit(asMinutes: Boolean) {
        val field = activeField ?: return
        if (digits.isNotEmpty() && field.accepts(digits)) field.apply(digits, asMinutes)
    }

    private fun close() {
        activeId = null
        activeField = null
        digits = ""
    }
}
