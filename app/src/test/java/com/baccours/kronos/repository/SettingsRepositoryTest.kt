package com.baccours.kronos.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.ThemeMode
import com.baccours.kronos.model.TimerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The settings against a real preferences file, including the states no user can create by hand. */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "kronos_test.preferences_pb")

    private fun TestScope.openStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = kronosCorruptionHandler,
        scope = backgroundScope,
        produceFile = { file }
    )

    @Test
    fun nothingSavedGivesTheDefaults() = runTest {
        assertEquals(AppSettings(), SettingsRepository(openStore()).settings.first())
    }

    @Test
    fun savedSettingsComeBack() = runTest {
        val repository = SettingsRepository(openStore())
        val settings = AppSettings(beeps = false, voice = false, theme = ThemeMode.DARK)
        assertTrue(repository.save(settings))
        assertEquals(settings, repository.settings.first())
    }

    @Test
    fun anUnknownThemeNameFallsBackToTheDefaultAndKeepsTheOtherSettings() = runTest {
        val store = openStore()
        store.edit {
            it[stringPreferencesKey("theme")] = "SOLARIZED" // e.g. written by a newer version
            it[booleanPreferencesKey("beeps")] = false
        }
        assertEquals(AppSettings(beeps = false, voice = true, theme = ThemeMode.SYSTEM), SettingsRepository(store).settings.first())
    }

    @Test
    fun aValueOfTheWrongTypeGivesTheDefaultsInsteadOfCrashing() = runTest {
        val store = openStore()
        store.edit { it[stringPreferencesKey("beeps")] = "yes" }
        assertEquals(AppSettings(), SettingsRepository(store).settings.first())
    }

    @Test
    fun aCorruptFileIsReplacedAndSavingWorksAgain() = runTest {
        file.writeBytes(ByteArray(32) { 0xFF.toByte() }) // not a valid preferences file
        val repository = SettingsRepository(openStore())

        assertEquals(AppSettings(), repository.settings.first())

        val settings = AppSettings(beeps = false, theme = ThemeMode.LIGHT)
        assertTrue("saving must work after the corruption", repository.save(settings))
        assertEquals(settings, repository.settings.first())
    }

    @Test
    fun savingTheSettingsKeepsTheSavedWorkout() = runTest {
        // Both repositories share one file: neither may wipe the other's keys.
        val store = openStore()
        val timers = TimerPreferencesRepository(store)
        val config = TimerConfig(sets = 4, rounds = 3)
        assertTrue(timers.saveTimerConfig(config))

        assertTrue(SettingsRepository(store).save(AppSettings(voice = false)))

        assertEquals(config.sanitized(), timers.timerConfigFlow.first())
    }
}
