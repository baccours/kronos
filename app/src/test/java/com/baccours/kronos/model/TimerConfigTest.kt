package com.baccours.kronos.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerConfigTest {

    private fun task(s: Int, type: TaskType = TaskType.WORK) = Task(type = type, durationSeconds = s)

    @Test
    fun sanitizedForcesEveryValueIntoTheRangeTheUiAllows() {
        val dirty = TimerConfig(
            sets = 0,
            rounds = 1_000,
            setRestSeconds = -5,
            timeCap = 999_999,
            totalTimeCap = -1,
            tasks = listOf(task(-30), task(1_000_000))
        )
        val clean = dirty.sanitized()
        assertEquals(1, clean.sets)
        assertEquals(TimerConfig.MAX_ROUNDS, clean.rounds)
        assertEquals(0, clean.setRestSeconds)
        assertEquals(TimerConfig.MAX_DURATION_SECONDS, clean.timeCap)
        assertEquals(0, clean.totalTimeCap)
        assertEquals(listOf(0, TimerConfig.MAX_DURATION_SECONDS), clean.tasks.map { it.durationSeconds })
    }

    @Test
    fun sanitizedLeavesAValidConfigUntouched() {
        val config = TimerConfig(
            sets = 3, rounds = 4, setRestSeconds = 60,
            tasks = listOf(Task("a", TaskType.WORK, 30), Task("b", TaskType.REST, 10))
        )
        assertEquals(config, config.sanitized())
    }

    @Test
    fun sanitizedGivesBlankAndRepeatedTaskIdsAFreshOne() {
        val clean = TimerConfig(
            tasks = listOf(
                Task("a", TaskType.WORK, 30),
                Task("a", TaskType.REST, 10),
                task(20),
                Task("task-2", TaskType.WORK, 5)
            )
        ).sanitized()
        val ids = clean.tasks.map { it.id }
        assertEquals("a", ids[0])
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isEmpty() })
    }

    @Test
    fun sanitizedLimitsTheNumberOfTasks() {
        val config = TimerConfig(tasks = List(TimerConfig.MAX_TASKS + 20) { task(10) })
        assertEquals(TimerConfig.MAX_TASKS, config.sanitized().tasks.size)
    }

    @Test
    @Suppress("KotlinConstantConditions")
    fun theLargestAllowedWorkoutStillFitsInAnInt() {
        val biggest = TimerConfig(
            sets = TimerConfig.MAX_SETS,
            rounds = TimerConfig.MAX_ROUNDS,
            setRestSeconds = TimerConfig.MAX_DURATION_SECONDS,
            tasks = List(TimerConfig.MAX_TASKS) { task(TimerConfig.MAX_DURATION_SECONDS) }
        ).sanitized()
        val exact = TimerConfig.MAX_TASKS.toLong() * TimerConfig.MAX_DURATION_SECONDS *
            TimerConfig.MAX_ROUNDS * TimerConfig.MAX_SETS +
            TimerConfig.MAX_DURATION_SECONDS.toLong() * (TimerConfig.MAX_SETS - 1)
        assertEquals(2_117_251_066L, exact)
        assertTrue(exact < Int.MAX_VALUE)
        assertEquals(exact.toInt(), biggest.totalDurationSeconds)
    }

    @Test
    fun maxTasksIsTheLargestNumberThatCannotOverflow() {
        fun longest(tasks: Int) = tasks.toLong() * TimerConfig.MAX_DURATION_SECONDS *
            TimerConfig.MAX_ROUNDS * TimerConfig.MAX_SETS +
            TimerConfig.MAX_DURATION_SECONDS.toLong() * (TimerConfig.MAX_SETS - 1)
        assertTrue(longest(TimerConfig.MAX_TASKS) <= Int.MAX_VALUE)
        assertTrue("one more task must overflow", longest(TimerConfig.MAX_TASKS + 1) > Int.MAX_VALUE)
    }

    @Test
    fun anUnsanitizedHugeConfigClampsInsteadOfOverflowing() {
        val huge = TimerConfig(sets = 99, rounds = 99, tasks = List(200) { task(TimerConfig.MAX_DURATION_SECONDS) })
        assertEquals(Int.MAX_VALUE, huge.totalDurationSeconds)
    }
}
