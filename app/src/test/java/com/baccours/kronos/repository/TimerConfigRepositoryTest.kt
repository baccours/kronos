package com.baccours.kronos.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The saved workout setup, including what a damaged or foreign file can contain. */
class TimerConfigRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val key = stringPreferencesKey("timer_config_json")

    private fun TestScope.openStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = kronosCorruptionHandler,
        scope = backgroundScope,
        produceFile = { File(tmp.root, "kronos_test.preferences_pb") }
    )

    private suspend fun DataStore<Preferences>.store(json: String) = edit { it[key] = json }

    @Test
    fun nothingSavedGivesTheDefaultSetup() = runTest {
        assertEquals(TimerConfig(), TimerConfigRepository(openStore()).timerConfigFlow.first())
    }

    @Test
    fun aSavedSetupComesBack() = runTest {
        val repository = TimerConfigRepository(openStore())
        val config = TimerConfig(
            mode = TimerMode.FOR_TIME,
            sets = 3,
            setRestSeconds = 45,
            timeCap = 600,
            rounds = 5,
            tasks = listOf(Task("a", TaskType.WORK, 40), Task("b", TaskType.REST, 20))
        )
        assertTrue(repository.saveTimerConfig(config))
        assertEquals(config.sanitized(), repository.timerConfigFlow.first())
    }

    @Test
    fun garbageInTheFileGivesTheDefaultSetup() = runTest {
        val store = openStore()
        store.store("{ this is not json")
        assertEquals(TimerConfig(), TimerConfigRepository(store).timerConfigFlow.first())
    }

    @Test
    fun aSetupWrittenByAnotherVersionWithAnUnknownModeFallsBackToDefaultModeAndKeepsTheRest() = runTest {
        val store = openStore()
        store.store("""{"mode":"HYPERSPACE","sets":4}""")
        assertEquals(TimerConfig(sets = 4), TimerConfigRepository(store).timerConfigFlow.first())
    }

    @Test
    fun aSetupWithAbsurdValuesIsBroughtBackWithinTheLimits() = runTest {
        // The engine's overflow guarantees rest on these limits, whatever the file says.
        val store = openStore()
        store.store("""{"sets":5000,"rounds":0,"timeCap":99999999}""")
        val loaded = TimerConfigRepository(store).timerConfigFlow.first()
        assertEquals(TimerConfig.MAX_SETS, loaded.sets)
        assertEquals(1, loaded.rounds)
        assertTrue(loaded.timeCap <= TimerConfig.MAX_DURATION_SECONDS)
    }
}
