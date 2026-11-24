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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** The settings against a real preferences file, including the states no user can create by hand. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "kronos_test.preferences_pb")

    private fun TestScope.openStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = kronosCorruptionHandler,
        scope = backgroundScope,
        produceFile = { file },
    )

    private fun TestScope.repository(store: DataStore<Preferences> = openStore()) =
        SettingsRepository(store, backgroundScope)

    /** What a new repository over [store] loads: the settings as they are saved. */
    private suspend fun TestScope.saved(store: DataStore<Preferences>): AppSettings {
        advanceUntilIdle()
        var result = repository(store).settings.filterNotNull().first()
        var attempts = 0
        while ((result == AppSettings()) && (attempts < 20)) {
            delay(10.milliseconds)
            val check = repository(store).settings.filterNotNull().first()
            if (check != AppSettings()) {
                result = check
                break
            }
            attempts++
        }
        return result
    }

    @Test
    fun theSettingsAreUnknownUntilLoadedAndTheDefaultsApplyMeanwhile() = runTest {
        val repository = repository()
        assertNull(repository.settings.value)
        assertEquals(AppSettings(), repository.current)
    }

    @Test
    fun nothingSavedGivesTheDefaults() = runTest {
        assertEquals(AppSettings(), saved(openStore()))
    }

    @Test
    fun anUpdateIsVisibleAtOnceAndSaved() = runTest {
        val store = openStore()
        val repository = repository(store)
        repository.settings.filterNotNull().first()
        val expected = AppSettings(beeps = false, voice = false, theme = ThemeMode.DARK)

        repository.update { expected }

        assertEquals(expected, repository.current) // what the service reads for the next cue
        advanceUntilIdle()
        assertEquals(expected, saved(store))
    }

    @Test
    fun quickSuccessiveChangesAllCount() = runTest {
        val store = openStore()
        val repository = repository(store)
        repository.settings.filterNotNull().first()

        repository.update { it.copy(beeps = false) }
        repository.update { it.copy(voice = false) }

        val expected = AppSettings(beeps = false, voice = false)
        assertEquals(expected, repository.current)
        advanceUntilIdle()
        assertEquals(expected, saved(store))
    }

    @Test
    fun aLateLoadNeverOverwritesWhatTheUserAlreadyChanged() = runTest {
        val repository = repository() // the disk read has not run yet
        repository.update { it.copy(beeps = false) }

        advanceUntilIdle()

        assertEquals(AppSettings(beeps = false), repository.current)
    }

    @Test
    fun anUnknownThemeNameFallsBackToTheDefaultAndKeepsTheOtherSettings() = runTest {
        val store = openStore()
        store.edit {
            it[stringPreferencesKey("theme")] = "SOLARIZED" // e.g. written by a newer version
            it[booleanPreferencesKey("beeps")] = false
        }
        assertEquals(AppSettings(beeps = false, voice = true, theme = ThemeMode.SYSTEM), saved(store))
    }

    @Test
    fun aValueOfTheWrongTypeGivesTheDefaultsInsteadOfCrashing() = runTest {
        val store = openStore()
        store.edit { it[stringPreferencesKey("beeps")] = "yes" }
        assertEquals(AppSettings(), saved(store))
    }

    @Test
    fun aCorruptFileIsReplacedAndSavingWorksAgain() = runTest {
        file.writeBytes(ByteArray(32) { 0xFF.toByte() }) // not a valid preferences file
        val store = openStore()
        val repository = repository(store)
        assertEquals(AppSettings(), repository.settings.filterNotNull().first())

        val settings = AppSettings(beeps = false, theme = ThemeMode.LIGHT)
        repository.update { settings }
        advanceUntilIdle()

        assertEquals(settings, saved(store))
    }

    @Test
    fun savingTheSettingsKeepsTheSavedWorkout() = runTest {
        // Both repositories share one file: neither may wipe the other's keys.
        val store = openStore()
        val timers = TimerConfigRepository(store)
        val config = TimerConfig(sets = 4, rounds = 3)
        timers.saveTimerConfig(config)

        val repository = repository(store)
        repository.settings.filterNotNull().first()
        repository.update { it.copy(voice = false) }
        advanceUntilIdle()

        assertEquals(config.sanitized(), timers.timerConfigFlow.first())
    }
}
