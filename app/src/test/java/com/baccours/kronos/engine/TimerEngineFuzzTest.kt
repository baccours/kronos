package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Random workouts against the engine: an independent model for undisturbed runs, and invariants
 * for random commands, clock jumps and stalls. Seeds are fixed, so a failure reproduces.
 */
class TimerEngineFuzzTest {

    private class Seg(val phase: Phase, val seconds: Int, val set: Int)

    /** What the workout should be, written from the config alone (not from [WorkoutPlan]). */
    private fun expectedSegments(c: TimerConfig): List<Seg> = buildList {
        add(Seg(Phase.READY, WorkoutPlan.READY_SECONDS, 1))
        for (set in 1..c.sets) {
            if (c.mode == TimerMode.TIMER) {
                repeat(c.rounds) {
                    for (t in c.tasks.filter { it.durationSeconds > 0 }) {
                        add(Seg(if (t.type == TaskType.WORK) Phase.WORK else Phase.REST, t.durationSeconds, set))
                    }
                }
            } else {
                add(Seg(Phase.WORK, c.timeCap, set))
            }
            if (set < c.sets && c.setRestSeconds > 0) add(Seg(Phase.SET_REST, c.setRestSeconds, set))
        }
    }

    private fun randomConfig(r: Random): TimerConfig? {
        val tasks = List(r.nextInt(0, 6)) {
            val seconds = when (r.nextInt(4)) {
                0 -> 0
                1 -> r.nextInt(1, 4)
                2 -> r.nextInt(1, 15)
                else -> r.nextInt(1, 90)
            }
            Task("t$it", if (r.nextBoolean()) TaskType.WORK else TaskType.REST, seconds)
        }
        return TimerConfig(
            mode = if (r.nextInt(3) == 0) TimerMode.FOR_TIME else TimerMode.TIMER,
            sets = r.nextInt(1, 4),
            setRestSeconds = if (r.nextBoolean()) 0 else r.nextInt(1, 20),
            timeCap = if (r.nextBoolean()) 0 else r.nextInt(1, 40),
            totalTimeCap = if (r.nextBoolean()) 0 else r.nextInt(1, 120),
            rounds = r.nextInt(1, 4),
            tasks = tasks
        ).sanitized().takeIf { it.canStart }
    }

    @Test
    fun anUndisturbedWorkoutFollowsTheModelToTheMillisecond() {
        for (seed in 1L..SEEDS) {
            val r = Random(seed)
            val config = randomConfig(r)?.takeIf { it.mode == TimerMode.TIMER } ?: continue
            val expected = expectedSegments(config)
            val plan = WorkoutPlan(config)
            val what = "seed $seed, $config"

            assertEquals("plan size, $what", expected.size, plan.size)
            expected.forEachIndexed { i, seg ->
                assertEquals("phase $i, $what", seg.phase, plan[i].phase)
                assertEquals("seconds $i, $what", seg.seconds, plan[i].durationSeconds)
                assertEquals("set $i, $what", seg.set, plan[i].set)
            }

            val ends = expected.runningFold(0L) { end, seg -> end + seg.seconds * 1000L }
            val start = r.nextLong(0, 1_000_000)
            val engine = TimerEngine(config)
            engine.start(start)
            var now = start
            while (true) {
                now += r.nextLong(1, if (r.nextInt(10) == 0) 30_000 else 1_500) // some very late ticks
                val state = engine.tick(now).state
                val elapsed = now - start
                if (elapsed >= ends.last()) {
                    assertEquals("finished, $what", TimerStatus.FINISHED, state.status)
                    assertEquals("planned time, $what", config.totalDurationSeconds, state.totalElapsedSeconds)
                    break
                }
                val index = ends.indexOfLast { it <= elapsed }
                assertEquals("index at $elapsed, $what", index, state.segmentIndex)
                assertEquals("elapsed at $elapsed, $what", elapsed - ends[index], state.segmentElapsedMs)
            }
        }
    }

    @Test
    fun randomCommandsAndStallsKeepTheStateConsistent() {
        for (seed in 1L..SEEDS) chaos(seed)
    }

    /** Commands and ticks in time order, like the controller on the main thread; a stalled thread runs the tick late. */
    private fun chaos(seed: Long) {
        val r = Random(seed)
        val config = randomConfig(r) ?: return
        val plan = WorkoutPlan(config)
        val engine = TimerEngine(config)
        val isTimer = config.mode == TimerMode.TIMER
        var now = r.nextLong(0, 1_000_000)
        engine.start(now)
        var nextTick: Long? = now + engine.millisUntilNextTick(now)

        var index = 0
        var total = 0
        var remaining = Int.MAX_VALUE
        var finished: TimerState? = null

        fun verify(op: String, result: EngineResult) {
            val s = result.state
            val what = "seed $seed after $op, $config"
            finished?.let {
                assertEquals("left FINISHED, $what", TimerStatus.FINISHED, s.status)
                assertEquals("laps changed after the end, $what", it.laps, s.laps)
                assertEquals("time changed after the end, $what", it.totalElapsedSeconds, s.totalElapsedSeconds)
                assertEquals("cues after the end, $what", emptyList<Cue>(), result.cues)
            }
            if (s.status == TimerStatus.FINISHED && finished == null) finished = s
            check(s.segmentIndex >= index) { "segment went backwards, $what" }
            check(s.totalElapsedSeconds >= total) { "total time went backwards, $what" }
            check(s.segmentElapsedMs >= 0 && s.lapElapsedSeconds >= 0) { "negative time, $what" }
            check(s.laps.none { it.durationMs < 0 }) { "negative lap, $what" }
            if (isTimer) {
                val left = checkNotNull(s.totalRemainingSeconds) { "no time left shown, $what" }
                check(left in 0..remaining) { "time left went from $remaining to $left, $what" }
                check(s.totalElapsedSeconds <= config.totalDurationSeconds) { "more than planned, $what" }
                remaining = left
            }
            if (s.status != TimerStatus.FINISHED) {
                check((plan.getOrNull(s.segmentIndex + 1) == null) == (s.next == null)) { "wrong next, $what" }
            }
            if (s.status == TimerStatus.RUNNING) {
                check(engine.millisUntilNextTick(now) in 1..1_011) { "bad tick delay, $what" }
            }
            index = s.segmentIndex
            total = s.totalElapsedSeconds
        }

        fun reschedule() {
            nextTick = if (engine.status == TimerStatus.RUNNING) now + engine.millisUntilNextTick(now) else null
        }

        repeat(300) {
            val stall = r.nextInt(25) == 0
            val target = now + when (r.nextInt(5)) {
                0 -> r.nextLong(1, 50)
                1 -> r.nextLong(1, 1_100)
                2 -> r.nextLong(500, 2_500)
                3 -> 1_000
                else -> if (stall) r.nextLong(2_000, 60_000) else r.nextLong(1, 800)
            }
            while (nextTick?.let { it <= target } == true) {
                now = if (stall) target else maxOf(now, nextTick!!)
                verify("tick", engine.tick(now))
                reschedule()
                if (stall) break
            }
            now = maxOf(now, target)

            val before = engine.status
            val active = before == TimerStatus.RUNNING || before == TimerStatus.PAUSED
            when (r.nextInt(6)) {
                0 -> verify("pause", engine.pause(now)).also { if (before == TimerStatus.RUNNING) nextTick = null }
                1 -> verify("resume", engine.resume(now)).also { if (before == TimerStatus.PAUSED) reschedule() }
                2 -> verify("skip", engine.skip(now)).also { if (active) reschedule() }
                3 -> verify("lap", engine.lap(now))
                else -> verify("look", EngineResult(engine.snapshot(now)))
            }
        }
    }

    private companion object {
        const val SEEDS = 3_000L
    }
}
