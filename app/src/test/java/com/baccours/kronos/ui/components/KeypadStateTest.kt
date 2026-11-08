package com.baccours.kronos.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadStateTest {

    /** Test recorder for keypad applications. */
    private class Recorder {
        val applied = ArrayList<Pair<String, Boolean>>()
    }

    private fun time(id: String, recorder: Recorder) = KeypadField(
        id = id,
        kind = KeypadKind.TIME,
        maxDigits = 4,
        apply = { digits, asMinutes -> recorder.applied += "$id:$digits" to asMinutes },
        accepts = { true }
    )

    private fun number(id: String, recorder: Recorder, range: IntRange = 1..99) = KeypadField(
        id = id,
        kind = KeypadKind.NUMBER,
        maxDigits = range.last.toString().length,
        apply = { digits, _ -> digits.toIntOrNull()?.takeIf { it in range }?.let { recorder.applied += "$id:$it" to false } },
        accepts = { digits -> digits.toIntOrNull()?.let { it in range } == true }
    )

    private fun KeypadState.typeAll(text: String) = text.forEach { type(it) }

    @Test
    fun startsClosed() {
        val keypad = KeypadState()
        assertFalse(keypad.isOpen)
        assertNull(keypad.activeField)
        assertEquals("", keypad.digits)
    }

    @Test
    fun completingTheDigitsCommitsAndCloses() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(time("work", rec))
        keypad.typeAll("0030")
        assertEquals(listOf("work:0030" to false), rec.applied)
        assertFalse(keypad.isOpen)
        assertEquals("", keypad.digits)
    }

    @Test
    fun okCommitsWhatWasTypedAndCloses() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(time("work", rec))
        keypad.typeAll("45")
        keypad.commitAndClose()
        assertEquals(listOf("work:45" to false), rec.applied)
        assertFalse(keypad.isOpen)
    }

    @Test
    fun minReadsTheDigitsAsMinutes() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(time("work", rec))
        keypad.typeAll("2")
        keypad.commitAsMinutesAndClose()
        assertEquals(listOf("work:2" to true), rec.applied)
    }

    @Test
    fun tappingAnotherFieldCommitsThePreviousOneAndMovesOn() {
        val rec = Recorder()
        val keypad = KeypadState()
        val work = time("work", rec)
        val rest = time("rest", rec)
        keypad.open(work)
        keypad.typeAll("30")
        keypad.open(rest) // no dismissing step in between
        assertEquals(listOf("work:30" to false), rec.applied)
        assertEquals("rest", keypad.activeId)
        assertEquals("", keypad.digits)
        keypad.typeAll("10")
        keypad.commitAndClose()
        assertEquals(listOf("work:30" to false, "rest:10" to false), rec.applied)
    }

    @Test
    fun movingOnWithNothingTypedChangesNothing() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(time("a", rec))
        keypad.open(time("b", rec))
        keypad.commitAndClose()
        assertTrue(rec.applied.isEmpty())
    }

    @Test
    fun tappingTheActiveFieldAgainKeepsWhatWasTyped() {
        val rec = Recorder()
        val keypad = KeypadState()
        val work = time("work", rec)
        keypad.open(work)
        keypad.typeAll("12")
        keypad.open(work)
        assertEquals("12", keypad.digits)
        assertTrue(rec.applied.isEmpty())
    }

    @Test
    fun cancelThrowsTheDigitsAway() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(time("work", rec))
        keypad.typeAll("99")
        keypad.cancel()
        assertTrue(rec.applied.isEmpty())
        assertFalse(keypad.isOpen)
    }

    @Test
    fun clearThenMovingOnActsAsACancel() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(time("a", rec))
        keypad.typeAll("99")
        keypad.clear()
        keypad.open(time("b", rec))
        assertTrue(rec.applied.isEmpty())
    }

    @Test
    fun deleteRemovesTheLastDigit() {
        val keypad = KeypadState()
        keypad.open(time("a", Recorder()))
        keypad.typeAll("123")
        keypad.delete()
        assertEquals("12", keypad.digits)
        keypad.delete(); keypad.delete(); keypad.delete()
        assertEquals("", keypad.digits)
    }

    @Test
    fun anOutOfRangeNumberIsIgnored() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(number("sets", rec))
        keypad.typeAll("00") // complete, but not in 1..99
        assertTrue(rec.applied.isEmpty())
        assertFalse(keypad.isOpen)

        keypad.open(number("sets", rec))
        keypad.typeAll("0")
        assertFalse(keypad.canSubmit)
        keypad.type('7') // "07" completes the entry: committed as 7
        assertEquals(listOf("sets:7" to false), rec.applied)
    }

    @Test
    fun aNumberCommitsAsSoonAsItsDigitsAreComplete() {
        val rec = Recorder()
        val keypad = KeypadState()
        keypad.open(number("rounds", rec))
        keypad.typeAll("12")
        assertEquals(listOf("rounds:12" to false), rec.applied)
        assertFalse(keypad.isOpen)
    }

    @Test
    fun okIsAlwaysPossibleWhenNothingIsTypedAndOtherwiseNeedsAValidNumber() {
        val keypad = KeypadState()
        keypad.open(number("sets", Recorder()))
        assertTrue(keypad.canSubmit) // nothing typed: OK just closes
        keypad.type('5')
        assertTrue(keypad.canSubmit)
    }

    @Test
    fun extraDigitsAndNonDigitsAreIgnored() {
        val keypad = KeypadState()
        keypad.open(time("a", Recorder()))
        keypad.type('x')
        assertEquals("", keypad.digits)
    }

    @Test
    fun typingWithoutAnActiveFieldDoesNothing() {
        val keypad = KeypadState()
        keypad.type('5')
        assertEquals("", keypad.digits)
    }

    @Test
    fun aFieldReattachedAfterARotationCanCommit() {
        // Only the id and the digits survive a rotation; the field object is new.
        val keypad = KeypadState(activeId = "work", digits = "45")
        assertTrue(keypad.isOpen)
        assertNull(keypad.activeField)
        val rec = Recorder()
        keypad.attach(time("rest", rec)) // not the active one: ignored
        assertNull(keypad.activeField)
        keypad.attach(time("work", rec))
        keypad.commitAndClose()
        assertEquals(listOf("work:45" to false), rec.applied)
    }

    @Test
    fun cancelIfOnlyClosesTheMatchingField() {
        val keypad = KeypadState()
        keypad.open(time("a", Recorder()))
        keypad.cancelIf("b")
        assertTrue(keypad.isOpen)
        keypad.cancelIf("a")
        assertFalse(keypad.isOpen)
    }

    @Test
    fun discardDigitsOnlyAffectsTheMatchingField() {
        val keypad = KeypadState()
        keypad.open(time("a", Recorder()))
        keypad.typeAll("12")
        keypad.discardDigits("b")
        assertEquals("12", keypad.digits)
        keypad.discardDigits("a")
        assertEquals("", keypad.digits)
    }
}
