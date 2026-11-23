package com.baccours.kronos.ui.setup

import com.baccours.kronos.MainDispatcherRule
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.repository.TimerConfigStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Emits [initial] as the saved setup, or nothing until [loaded] is called when it is null. */
    private class FakeStorage(initial: TimerConfig? = TimerConfig()) : TimerConfigStorage {
        private val flow = MutableSharedFlow<TimerConfig>(replay = 1)
        val saved = ArrayList<TimerConfig>()
        var succeeds = true

        init { if (initial != null) flow.tryEmit(initial) }

        suspend fun loaded(config: TimerConfig) = flow.emit(config)

        override val timerConfigFlow: Flow<TimerConfig> = flow
        override suspend fun saveTimerConfig(config: TimerConfig): Boolean {
            saved += config
            return succeeds
        }
    }

    private fun work(id: String, s: Int) = Task(id, TaskType.WORK, s)
    private fun rest(id: String, s: Int) = Task(id, TaskType.REST, s)

    private fun viewModel(storage: FakeStorage) = SetupViewModel(storage)

    private fun SetupViewModel.config() = uiState.value!!

    // ---- Loading -----------------------------------------------------------------------------

    @Test
    fun nothingIsShownAndEditsAreIgnoredUntilTheSavedSetupLoads() = runTest {
        val storage = FakeStorage(initial = null)
        val vm = viewModel(storage)
        assertNull(vm.uiState.value)

        vm.updateSets(5) // too early: must not invent a config
        assertNull(vm.uiState.value)

        val saved = TimerConfig(sets = 3)
        storage.loaded(saved)
        assertEquals(saved, vm.uiState.value)
    }

    // ---- Every edit is clamped ---------------------------------------------------------------

    @Test
    fun editsAreClampedToTheAllowedRanges() {
        val vm = viewModel(FakeStorage(TimerConfig(tasks = listOf(work("a", 30)))))

        vm.updateSets(500)
        vm.updateRounds(0)
        vm.updateSetRest(-4)
        vm.updateTimeCap(1_000_000)
        vm.updateTotalTimeCap(-1)
        vm.updateTaskDuration("a", 1_000_000)

        val config = vm.config()
        assertEquals(TimerConfig.MAX_SETS, config.sets)
        assertEquals(1, config.rounds)
        assertEquals(0, config.setRestSeconds)
        assertEquals(TimerConfig.MAX_DURATION_SECONDS, config.timeCap)
        assertEquals(0, config.totalTimeCap)
        assertEquals(TimerConfig.MAX_DURATION_SECONDS, config.tasks.single().durationSeconds)
    }

    @Test
    fun switchingModeKeepsTheOtherModesSettings() {
        val vm = viewModel(FakeStorage(TimerConfig(rounds = 4, timeCap = 120)))
        vm.updateMode(TimerMode.FOR_TIME)
        vm.updateMode(TimerMode.TIMER)
        assertEquals(4, vm.config().rounds)
        assertEquals(120, vm.config().timeCap)
    }

    // ---- Tasks -------------------------------------------------------------------------------

    @Test
    fun aNewTaskStartsWithTheDurationOfTheLastTaskOfTheSameType() {
        val vm = viewModel(FakeStorage(TimerConfig(tasks = listOf(work("w1", 40), rest("r1", 15), work("w2", 20)))))

        vm.addTask(TaskType.WORK)
        vm.addTask(TaskType.REST)

        val added = vm.config().tasks.drop(3)
        assertEquals(listOf(TaskType.WORK to 20, TaskType.REST to 15), added.map { it.type to it.durationSeconds })
    }

    @Test
    fun aNewTaskFallsBackToTheLastTaskOfAnyTypeThenToThirtySeconds() {
        val onlyWork = viewModel(FakeStorage(TimerConfig(tasks = listOf(work("w1", 45)))))
        onlyWork.addTask(TaskType.REST)
        assertEquals(45, onlyWork.config().tasks.last().durationSeconds)

        val empty = viewModel(FakeStorage(TimerConfig(tasks = emptyList())))
        empty.addTask(TaskType.WORK)
        assertEquals(30, empty.config().tasks.single().durationSeconds)
    }

    @Test
    fun newTasksGetUniqueIdsAndTheListIsCappedAtTheMaximum() {
        val vm = viewModel(FakeStorage(TimerConfig(tasks = emptyList())))
        repeat(TimerConfig.MAX_TASKS + 5) { vm.addTask() }

        val tasks = vm.config().tasks
        assertEquals(TimerConfig.MAX_TASKS, tasks.size)
        assertEquals(tasks.size, tasks.map { it.id }.toSet().size)
        assertTrue(tasks.all { it.id.isNotEmpty() })
    }

    @Test
    fun removingAndEditingATaskTargetsItByIdOnly() {
        val vm = viewModel(FakeStorage(TimerConfig(tasks = listOf(work("a", 10), rest("b", 20), work("c", 30)))))

        vm.updateTaskDuration("b", 25)
        assertEquals(listOf(10, 25, 30), vm.config().tasks.map { it.durationSeconds })

        vm.removeTask("a")
        assertEquals(listOf("b", "c"), vm.config().tasks.map { it.id })

        vm.removeTask("missing")
        vm.updateTaskDuration("missing", 99)
        assertEquals(listOf("b", "c"), vm.config().tasks.map { it.id })
    }

    // ---- Saving ------------------------------------------------------------------------------

    @Test
    fun nothingIsSavedForTheConfigThatWasJustLoaded() = runTest {
        val storage = FakeStorage()
        viewModel(storage)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun anEditIsSavedOnceTheDebounceElapses() = runTest {
        val storage = FakeStorage()
        val vm = viewModel(storage)

        vm.updateSets(3)
        advanceTimeBy(299.milliseconds)
        runCurrent()
        assertTrue("too early", storage.saved.isEmpty())

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(listOf(vm.config()), storage.saved)
        assertEquals(3, storage.saved.single().sets)
    }

    @Test
    fun aBurstOfEditsIsSavedOnceWithTheFinalValue() = runTest {
        val storage = FakeStorage()
        val vm = viewModel(storage)

        vm.updateSets(2)
        advanceTimeBy(100.milliseconds)
        vm.updateSets(3)
        advanceTimeBy(100.milliseconds)
        vm.updateSets(4)
        advanceTimeBy(301.milliseconds)
        runCurrent()

        assertEquals(listOf(4), storage.saved.map { it.sets })
    }

    @Test
    fun aFailedSaveIsNotRememberedAsSaved() = runTest {
        val storage = FakeStorage(TimerConfig(sets = 1)).apply { succeeds = false }
        val vm = viewModel(storage)

        vm.updateSets(3)
        advanceTimeBy(301.milliseconds)
        runCurrent()
        assertEquals(listOf(3), storage.saved.map { it.sets }) // attempted, failed

        // Back to what is on disk: nothing to write.
        vm.updateSets(1)
        advanceTimeBy(301.milliseconds)
        runCurrent()
        assertEquals(listOf(3), storage.saved.map { it.sets })

        // A different change is attempted again, and a success is remembered.
        storage.succeeds = true
        vm.updateSets(4)
        advanceTimeBy(301.milliseconds)
        runCurrent()
        assertEquals(listOf(3, 4), storage.saved.map { it.sets })
        assertNotEquals(storage.saved[0], storage.saved[1])
    }
}
