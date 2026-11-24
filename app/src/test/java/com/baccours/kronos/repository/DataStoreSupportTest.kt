package com.baccours.kronos.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The failure paths of the preferences helpers: a disk that fails or a file that cannot be decoded. */
class DataStoreSupportTest {

    private class FailingStore(
        override val data: Flow<Preferences> = flowOf(emptyPreferences()),
        private val onUpdate: () -> Unit = {}
    ) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            onUpdate()
            return transform(emptyPreferences())
        }
    }

    /** Not an [Exception]: something that must never be swallowed. */
    private class Fatal : Error("fatal")

    // ---- readSafely ----

    @Test
    fun readSafelyEmitsTheDecodedValue() = runTest {
        val store = FailingStore()
        assertEquals("decoded", store.readSafely("test", "default") { "decoded" }.first())
    }

    @Test
    fun readSafelyFallsBackToTheDefaultWhenTheDiskFails() = runTest {
        val store = FailingStore(data = flow { throw IOException("disk") })
        assertEquals("default", store.readSafely("test", "default") { "decoded" }.first())
    }

    @Test
    fun readSafelyFallsBackToTheDefaultWhenDecodingThrows() = runTest {
        val store = FailingStore()
        val value = store.readSafely("test", "default") { error("a value of an unexpected type") }.first()
        assertEquals("default", value)
    }

    @Test
    fun readSafelyDoesNotHideAnError() = runTest {
        val store = FailingStore()
        try {
            store.readSafely("test", "default") { throw Fatal() }.first()
            fail("an Error must propagate")
        } catch (_: Fatal) {
            // expected
        }
    }

    // ---- editSafely ----

    @Test
    fun editSafelyReportsSuccess() = runTest {
        var updates = 0
        val store = FailingStore(onUpdate = { updates++ })
        assertTrue(store.editSafely("test") {})
        assertEquals(1, updates)
    }

    @Test
    fun editSafelyReportsAFailedWriteInsteadOfThrowing() = runTest {
        val store = FailingStore(onUpdate = { throw IOException("disk full") })
        assertFalse(store.editSafely("test") {})
    }

    @Test
    fun editSafelyDoesNotHideAProgrammingError() = runTest {
        val store = FailingStore()
        try {
            store.editSafely("test") { throw IllegalStateException("bug") }
            fail("a bug must not be reported as a failed save")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
