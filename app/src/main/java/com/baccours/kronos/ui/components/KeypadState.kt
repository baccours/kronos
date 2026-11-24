package com.baccours.kronos.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Interpretation mode for keypad input. */
enum class KeypadKind {
    /** Duration input (mmss or minutes). */
    TIME,

    /** Integer input. */
    NUMBER
}

/** Identifies a keypad field. Must be stable across recompositions and rotation. */
@JvmInline
value class FieldId(val key: String)

/** Keypad field binding contract. */
@Stable
class KeypadField(
    val id: FieldId,
    val kind: KeypadKind,
    /** Maximum allowed digits. */
    val maxDigits: Int,
    /** Applies typed digits to field value. */
    val apply: (digits: String, asMinutes: Boolean) -> Unit,
    /** Validates typed digits before applying. */
    val accepts: (digits: String) -> Boolean
)

/**
 * Keypad controller managing active field state and typed digits.
 */
@Stable
class KeypadState(activeId: FieldId? = null, digits: String = "") {

    var activeId: FieldId? by mutableStateOf(activeId)
        private set

    /** Currently typed digit string. */
    var digits: String by mutableStateOf(digits)
        private set

    var activeField: KeypadField? by mutableStateOf(null)
        private set

    /** Open once the active field is attached; a restored id with no field stays closed. */
    val isOpen: Boolean get() = activeField != null

    /** True if empty or containing valid accepted digits. */
    val canSubmit: Boolean
        get() = digits.isEmpty() || activeField?.accepts?.invoke(digits) == true

    /** Commits previous field input and activates [field]. */
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

    /** Re-attaches [field] instance if it matches [activeId]. */
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

    /** Clears typed digits if [fieldId] matches active field. */
    fun discardDigits(fieldId: FieldId) {
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

    /** Cancels active editing without committing. */
    fun cancel() = close()

    /** Cancels editing if [fieldId] is active. */
    fun cancelIf(fieldId: FieldId) {
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
