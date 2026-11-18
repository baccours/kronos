package com.baccours.kronos.ui.settings

import android.app.Application
import com.baccours.kronos.MainDispatcherRule
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import com.baccours.kronos.repository.SettingsStorage
import com.baccours.kronos.repository.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeStorage(var succeeds: Boolean = true) : SettingsStorage {
        val saved = ArrayList<AppSettings>()
        override val settings: Flow<AppSettings> = emptyFlow()
        override suspend fun save(settings: AppSettings): Boolean {
            saved += settings
            return succeeds
        }
    }

    private val store = SettingsStore()
    private val storage = FakeStorage()
    private fun viewModel() = SettingsViewModel(Application(), storage, store)

    @Test
    fun theSettingsAreUnknownUntilTheyAreLoaded() {
        assertNull(viewModel().settings.value)
    }

    @Test
    fun anUpdateIsVisibleAtOnceAndPersisted() {
        val vm = viewModel()
        store.load(AppSettings())
        val updated = AppSettings(beeps = false, theme = ThemeMode.entries.last())

        vm.update(updated)

        assertEquals(updated, vm.settings.value)
        assertEquals(updated, store.current) // what the service reads for the next cue
        assertEquals(listOf(updated), storage.saved)
    }

    @Test
    fun updatesArePersistedInTheOrderTheyWereMade() {
        val vm = viewModel()
        store.load(AppSettings())
        val first = AppSettings(beeps = false)
        val second = first.copy(voice = false)

        vm.update(first)
        vm.update(second)

        assertEquals(listOf(first, second), storage.saved)
        assertEquals(second, store.current)
    }

    @Test
    fun aFailedSaveKeepsTheChangeForThisSession() {
        storage.succeeds = false
        val vm = viewModel()
        store.load(AppSettings())
        val updated = AppSettings(beeps = false)

        vm.update(updated)

        assertEquals(updated, store.current)
        assertEquals(listOf(updated), storage.saved)
    }
}
