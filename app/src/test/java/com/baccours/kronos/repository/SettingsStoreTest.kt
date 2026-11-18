package com.baccours.kronos.repository

import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsStoreTest {

    private val loaded = AppSettings(beeps = false, voice = false, theme = ThemeMode.entries.last())

    @Test
    fun beforeTheSavedSettingsAreLoadedTheFlowIsNullAndTheDefaultsApply() {
        val store = SettingsStore()
        assertNull(store.settings.value)
        assertEquals(AppSettings(), store.current)
    }

    @Test
    fun loadPublishesTheSavedSettings() {
        val store = SettingsStore()
        store.load(loaded)
        assertEquals(loaded, store.settings.value)
        assertEquals(loaded, store.current)
    }

    @Test
    fun aLateLoadNeverOverwritesWhatTheUserAlreadyChanged() {
        val store = SettingsStore()
        val changed = AppSettings(beeps = false)
        store.set(changed)
        store.load(loaded) // the disk read finished after the user's first change
        assertEquals(changed, store.current)
    }

    @Test
    fun loadOnlyCountsTheFirstTime() {
        val store = SettingsStore()
        store.load(loaded)
        store.load(AppSettings())
        assertEquals(loaded, store.current)
    }

    @Test
    fun setReplacesTheSettingsImmediately() {
        val store = SettingsStore()
        store.load(loaded)
        val next = loaded.copy(voice = true)
        store.set(next)
        assertEquals(next, store.current)
    }

    @Test
    fun instancesAreIsolated() {
        val a = SettingsStore()
        val b = SettingsStore()
        a.set(loaded)
        assertNull(b.settings.value)
    }
}
