package com.baccours.kronos.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusControllerTest {

    private val main = FakeMainThread()
    private val system = FakeFocusSystem()
    private val focus = AudioFocusController(main, system)

    @Test
    fun theSystemIsAskedOnlyWhileWeHoldNoFocus() {
        assertTrue(focus.request())
        assertTrue(focus.request())
        assertEquals(1, system.requests)
    }

    @Test
    fun aDeniedRequestMeansSilenceAndIsAskedAgainNextTime() {
        system.grant = false // a call is in progress
        assertFalse(focus.request())
        assertFalse(focus.request())
        assertEquals(2, system.requests)

        system.grant = true // call over
        assertTrue(focus.request())
        assertEquals(3, system.requests)
    }

    @Test
    fun focusIsReleasedAfterTheDelayWhenIdle() {
        focus.request()
        focus.releaseAfter(300)

        main.advanceBy(299)
        assertEquals(0, system.abandons)
        main.advanceBy(1)
        assertEquals(1, system.abandons)

        focus.request() // we lost it: a new request goes to the system
        assertEquals(2, system.requests)
    }

    @Test
    fun aBusyAnnouncerKeepsTheFocusPastTheScheduledRelease() {
        var busy = true
        focus.isBusy = { busy }
        focus.request()
        focus.releaseAfter(300)

        main.advanceBy(300)
        assertEquals(0, system.abandons)

        busy = false
        focus.releaseAfter(300)
        main.advanceBy(300)
        assertEquals(1, system.abandons)
    }

    @Test
    fun aNewRequestCancelsAPendingRelease() {
        focus.request()
        focus.releaseAfter(300)
        main.advanceBy(100)
        focus.request()
        main.advanceBy(10_000)
        assertEquals(0, system.abandons)
    }

    @Test
    fun releaseIsImmediateAndHarmlessWithoutFocus() {
        focus.release()
        assertEquals(0, system.abandons)

        focus.request()
        focus.release()
        focus.release()
        assertEquals(1, system.abandons)
    }

    @Test
    fun whenAnotherAppTakesTheFocusWeDropItBeforeTellingTheListener() {
        val order = ArrayList<String>()
        focus.onLost = { order += "lost(abandons=${system.abandons})" }
        focus.request()

        system.loseFocus() // incoming call

        assertEquals(listOf("lost(abandons=1)"), order)
        focus.request() // the next cue must ask the system again
        assertEquals(2, system.requests)
    }
}
