package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** The plan is the single source of "what comes when"; the engine, cues and preload all derive from it. */
class WorkoutPlanTest {

    private fun work(s: Int) = Task(type = TaskType.WORK, durationSeconds = s)
    private fun rest(s: Int) = Task(type = TaskType.REST, durationSeconds = s)

    private fun WorkoutPlan.describe() = map {
        "${it.phase.name} s${it.set} r${it.round} w${it.workIndex} ${it.durationSeconds}s"
    }

    @Test
    fun timerPlanRepeatsTheTasksPerRoundAndPutsTheSetRestBetweenSetsOnly() {
        val plan = WorkoutPlan(
            TimerConfig(sets = 2, rounds = 2, setRestSeconds = 7, tasks = listOf(work(5), rest(3)))
        )
        assertEquals(
            listOf(
                "READY s1 r1 w0 10s",
                "WORK s1 r1 w1 5s", "REST s1 r1 w0 3s",
                "WORK s1 r2 w1 5s", "REST s1 r2 w0 3s",
                "SET_REST s1 r2 w0 7s", // belongs to the set it ends; no rest after the last set
                "WORK s2 r1 w1 5s", "REST s2 r1 w0 3s",
                "WORK s2 r2 w1 5s", "REST s2 r2 w0 3s"
            ),
            plan.describe()
        )
        assertEquals(9, plan.lastIndex)
    }

    @Test
    fun withoutSetRestSetsFollowEachOtherDirectly() {
        val plan = WorkoutPlan(TimerConfig(sets = 2, tasks = listOf(work(5))))
        assertEquals(
            listOf("READY s1 r1 w0 10s", "WORK s1 r1 w1 5s", "WORK s2 r1 w1 5s"),
            plan.describe()
        )
    }

    @Test
    fun zeroDurationTasksAreDroppedButKeepTheNumberTheUserSees() {
        // The setup screen numbers rows per type, so "Work 2" must stay "Work 2" even when row 1 is 0 s.
        val plan = WorkoutPlan(TimerConfig(tasks = listOf(work(0), rest(0), work(5), rest(4))))
        assertEquals(
            listOf("READY s1 r1 w0 10s", "WORK s1 r1 w2 5s", "REST s1 r1 w0 4s"),
            plan.describe()
        )
    }

    @Test
    fun aTimerWithNothingToRunIsJustTheGetReadySegment() {
        val plan = WorkoutPlan(TimerConfig(tasks = listOf(work(0))))
        assertEquals(listOf("READY s1 r1 w0 10s"), plan.describe())
    }

    @Test
    fun forTimeHasOneOpenEndedWorkSegmentPerSetAndIgnoresRoundsAndTasks() {
        val plan = WorkoutPlan(
            TimerConfig(
                mode = TimerMode.FOR_TIME, sets = 3, rounds = 5, setRestSeconds = 20,
                tasks = listOf(work(5), rest(3))
            )
        )
        assertEquals(
            listOf(
                "READY s1 r1 w0 10s",
                "WORK s1 r1 w0 0s", "SET_REST s1 r1 w0 20s",
                "WORK s2 r1 w0 0s", "SET_REST s2 r1 w0 20s",
                "WORK s3 r1 w0 0s"
            ),
            plan.describe()
        )
    }

    @Test
    fun forTimeSetsAreCappedByTheTimeCap() {
        val plan = WorkoutPlan(TimerConfig(mode = TimerMode.FOR_TIME, sets = 2, timeCap = 90))
        assertEquals(90, plan[1].durationSeconds)
        assertEquals(90, plan[2].durationSeconds)
    }

    @Test
    fun theLargestWorkoutIsAddressedWithoutBuildingIt() {
        val tasks = List(TimerConfig.MAX_TASKS) { i -> if (i % 2 == 0) work(60) else rest(30) }
        val config = TimerConfig(
            sets = TimerConfig.MAX_SETS, rounds = TimerConfig.MAX_ROUNDS, tasks = tasks
        )
        val plan = WorkoutPlan(config)
        assertEquals(1 + 99 * 99 * 36, plan.size)
        val last = plan[plan.lastIndex]
        assertEquals(Phase.REST, last.phase)
        assertEquals(99, last.set)
        assertEquals(99, last.round)
        // Every index maps to a segment: a walk over the whole plan never throws.
        var count = 0
        for (i in plan.indices) { plan[i]; count++ }
        assertEquals(plan.size, count)
    }

    @Test
    fun theFirstSegmentOfEverySetIsFoundByTheStride() {
        // CuePolicy.speechNeeded relies on `1 + (set - 1) * stride` being the first segment of a set.
        val plan = WorkoutPlan(
            TimerConfig(sets = 4, rounds = 3, setRestSeconds = 5, tasks = listOf(work(5), rest(5)))
        )
        for (set in 1..4) {
            val first = plan[1 + (set - 1) * plan.stride]
            assertEquals(set, first.set)
            assertEquals(Phase.WORK, first.phase)
            assertEquals(1, first.round)
        }
    }
}
