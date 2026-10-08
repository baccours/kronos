package com.baccours.kronos.engine

import com.baccours.kronos.model.Lap
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.UpNext

/** Something the audio layer should do. Spoken text is always English (US). */
sealed interface Cue {
    data class Speak(val text: String, val interrupt: Boolean = false) : Cue
    data object Beep : Cue
    data object StartBuzzer : Cue
    data object Celebration : Cue
}

data class EngineResult(
    val state: TimerState,
    val cues: List<Cue> = emptyList()
)

/**
 * Pure, clock-injected workout state machine (no Android dependencies, unit-testable).
 *
 * The workout is flattened into a list of segments (get ready, work, rest, set rest, ...).
 * Zero-length items are dropped from the plan, so they can never stall the timer.
 *
 * TIMER mode: every segment counts down. Segments chain from their *planned* boundary, so
 * no drift accumulates.
 * FOR_TIME mode: every set is one open-ended count-up segment (optionally limited by the
 * time cap) in which laps can be logged; sets are separated by a countdown rest.
 *
 * All methods take `now` in milliseconds from any monotonic clock.
 */
class TimerEngine(
    private val config: TimerConfig,
    private val readySeconds: Int = DEFAULT_READY_SECONDS
) {
    private data class Segment(
        val phase: Phase,
        val set: Int,
        val round: Int,
        val workIndex: Int,
        /** 0 means open-ended. */
        val durationSeconds: Int
    )

    private val plan: List<Segment> = buildPlan()

    /** suffixSeconds[i] = total planned seconds of the non-READY segments after segment i. */
    private val suffixSeconds: IntArray = IntArray(plan.size).also { out ->
        var acc = 0
        for (i in plan.indices.reversed()) {
            out[i] = acc
            if (plan[i].phase != Phase.READY) acc += plan[i].durationSeconds
        }
    }

    private val isForTime = config.mode == TimerMode.FOR_TIME

    var status: TimerStatus = TimerStatus.NOT_STARTED
        private set

    private var index = 0
    private var segmentStartMs = 0L
    private var lapStartMs = 0L
    private var pausedAtMs = 0L
    private var completedMs = 0L
    private var lastElapsedSecond = 0
    private val laps = ArrayList<Lap>()

    // ---------------------------------------------------------------- commands

    fun start(now: Long): EngineResult {
        index = 0
        segmentStartMs = now
        lapStartMs = now
        completedMs = 0
        lastElapsedSecond = 0
        laps.clear()
        status = TimerStatus.RUNNING
        if (plan.isEmpty()) {
            val cues = ArrayList<Cue>()
            finish(cues)
            return EngineResult(snapshot(now), cues)
        }
        return EngineResult(snapshot(now), startCues(index))
    }

    fun tick(now: Long): EngineResult {
        if (status != TimerStatus.RUNNING) return EngineResult(snapshot(now))
        val cues = ArrayList<Cue>()
        while (true) {
            val seg = plan[index]
            val d = seg.durationSeconds
            val elapsedMs = now - segmentStartMs
            if (d > 0 && elapsedMs >= d * 1000L) {
                // Natural end of the segment: chain from the planned boundary.
                val boundary = segmentStartMs + d * 1000L
                completeSegment(boundary)
                cues.clear() // only the segment we land on is worth announcing
                if (index >= plan.lastIndex) {
                    finish(cues)
                    break
                }
                index++
                segmentStartMs = boundary
                lapStartMs = boundary
                lastElapsedSecond = 0
                cues += startCues(index)
                continue
            }
            val elapsedSecond = (elapsedMs / 1000).toInt()
            if (d > 0) {
                for (e in (lastElapsedSecond + 1)..elapsedSecond) {
                    countdownCues(seg, d - e)?.let { cues += it }
                }
            }
            lastElapsedSecond = maxOf(lastElapsedSecond, elapsedSecond)
            break
        }
        return EngineResult(snapshot(now), cues)
    }

    fun pause(now: Long): EngineResult {
        if (status == TimerStatus.RUNNING) {
            status = TimerStatus.PAUSED
            pausedAtMs = now
        }
        return EngineResult(snapshot(now))
    }

    fun resume(now: Long): EngineResult {
        if (status == TimerStatus.PAUSED) {
            val shift = now - pausedAtMs
            segmentStartMs += shift
            lapStartMs += shift
            status = TimerStatus.RUNNING
        }
        return EngineResult(snapshot(now))
    }

    /**
     * Ends the current segment right now: skips a timer item, ends "get ready" early,
     * or finishes a For Time set (logging its last lap). Works while paused as well.
     */
    fun skip(now: Long): EngineResult {
        if (status != TimerStatus.RUNNING && status != TimerStatus.PAUSED) {
            return EngineResult(snapshot(now))
        }
        val t = reference(now)
        val cues = ArrayList<Cue>()
        completeSegment(t)
        if (index >= plan.lastIndex) {
            finish(cues)
        } else {
            index++
            segmentStartMs = t
            lapStartMs = t
            lastElapsedSecond = 0
            cues += startCues(index)
        }
        return EngineResult(snapshot(now), cues)
    }

    /** For Time: logs a lap in the current set. */
    fun lap(now: Long): EngineResult {
        val seg = plan.getOrNull(index)
        if (status != TimerStatus.RUNNING || seg == null || !isLapSegment(seg)) {
            return EngineResult(snapshot(now))
        }
        addLap(seg.set, now - lapStartMs)
        lapStartMs = now
        return EngineResult(snapshot(now), listOf(Cue.Beep))
    }

    /** Milliseconds to wait before the next tick so that it lands just after a second boundary. */
    fun millisUntilNextTick(now: Long): Long {
        if (status != TimerStatus.RUNNING) return IDLE_TICK_MS
        val elapsed = (now - segmentStartMs).coerceAtLeast(0)
        return (1000 - (elapsed % 1000)) + TICK_SLACK_MS
    }

    fun snapshot(now: Long): TimerState {
        if (plan.isEmpty()) {
            return TimerState(status = status, config = config)
        }
        val t = reference(now)
        val seg = plan[index]
        val finished = status == TimerStatus.FINISHED
        val d = seg.durationSeconds
        val elapsedMs = if (finished) 0L else (t - segmentStartMs).coerceAtLeast(0)
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        val remaining = if (d > 0) (d - elapsedSeconds).coerceAtLeast(0) else 0
        val setLaps = laps.count { it.set == seg.set }
        val inTimedProgress = seg.phase != Phase.READY && !finished
        val totalElapsedMs = completedMs + if (inTimedProgress) elapsedMs else 0L

        return TimerState(
            status = status,
            config = config,
            phase = seg.phase,
            segmentIndex = index,
            currentSet = seg.set,
            currentRound = if (isForTime) setLaps + 1 else seg.round,
            workIndex = seg.workIndex,
            segmentSeconds = d,
            segmentElapsedMs = elapsedMs,
            elapsedSeconds = elapsedSeconds,
            remainingSeconds = remaining,
            lapElapsedSeconds = if (isLapSegment(seg) && !finished) {
                ((t - lapStartMs).coerceAtLeast(0) / 1000).toInt()
            } else 0,
            totalElapsedSeconds = (totalElapsedMs / 1000).toInt(),
            totalRemainingSeconds = if (isForTime) null else {
                if (finished) 0
                else (if (seg.phase == Phase.READY) 0 else remaining) + suffixSeconds[index]
            },
            next = if (finished) null else plan.getOrNull(index + 1)?.let {
                UpNext(it.phase, it.workIndex, it.set, it.durationSeconds)
            },
            laps = laps.toList()
        )
    }

    // ---------------------------------------------------------------- internals

    /** While paused, time is frozen at the moment of the pause. */
    private fun reference(now: Long): Long =
        if (status == TimerStatus.PAUSED) pausedAtMs else now

    private fun isLapSegment(seg: Segment) = isForTime && seg.phase == Phase.WORK

    private fun addLap(set: Int, durationMs: Long) {
        val number = laps.count { it.set == set } + 1
        laps += Lap(set = set, number = number, durationMs = durationMs.coerceAtLeast(0))
    }

    /** Books the time of the current segment and, for For Time sets, logs the closing lap. */
    private fun completeSegment(endMs: Long) {
        val seg = plan[index]
        if (isLapSegment(seg)) {
            val sinceLap = endMs - lapStartMs
            val hasLaps = laps.any { it.set == seg.set }
            // Pressing ROUND right before FINISH must not produce a sub-second ghost lap.
            if (!hasLaps || sinceLap >= MIN_CLOSING_LAP_MS) addLap(seg.set, sinceLap)
        }
        if (seg.phase != Phase.READY) {
            completedMs += (endMs - segmentStartMs).coerceAtLeast(0)
        }
    }

    private fun finish(cues: MutableList<Cue>) {
        status = TimerStatus.FINISHED
        cues.clear()
        cues += Cue.Celebration
        cues += Cue.Speak("Well done")
    }

    private fun buildPlan(): List<Segment> {
        val out = ArrayList<Segment>()
        if (readySeconds > 0) out += Segment(Phase.READY, 1, 1, 0, readySeconds)
        val indexed = config.indexedTasks
        for (set in 1..config.sets) {
            if (config.mode == TimerMode.TIMER) {
                for (round in 1..config.rounds) {
                    for (item in indexed) {
                        if (item.task.durationSeconds <= 0) continue
                        val isWork = item.task.type == TaskType.WORK
                        out += Segment(
                            phase = if (isWork) Phase.WORK else Phase.REST,
                            set = set,
                            round = round,
                            workIndex = if (isWork) item.displayIndex else 0,
                            durationSeconds = item.task.durationSeconds
                        )
                    }
                }
            } else {
                out += Segment(Phase.WORK, set, 1, 0, config.timeCap.coerceAtLeast(0))
            }
            if (set < config.sets && config.setRestSeconds > 0) {
                val lastRound = if (config.mode == TimerMode.TIMER) config.rounds else 1
                out += Segment(Phase.SET_REST, set, lastRound, 0, config.setRestSeconds)
            }
        }
        return out
    }

    private fun startCues(i: Int): List<Cue> {
        val seg = plan[i]
        val setPrefix = if (seg.set > 1 && isFirstOfSet(i)) "Set ${seg.set}. " else ""
        val roundPrefix = if (seg.round > 1 && isFirstOfRound(i)) "Round ${seg.round}. " else ""
        return when (seg.phase) {
            Phase.READY -> listOf(Cue.Speak("Get ready"))
            Phase.WORK -> listOf(
                Cue.StartBuzzer,
                Cue.Speak(
                    if (isForTime) "${setPrefix}Go"
                    else "$setPrefix$roundPrefix${workSpeech(seg.workIndex)}"
                )
            )
            Phase.REST -> listOf(Cue.StartBuzzer, Cue.Speak("${setPrefix}${roundPrefix}Rest"))
            Phase.SET_REST -> listOf(Cue.StartBuzzer, Cue.Speak("Rest between sets."))
        }
    }

    private fun workSpeech(workIndex: Int) = if (workIndex > 0) "Work $workIndex" else "Work"

    private fun isFirstOfSet(i: Int): Boolean {
        val seg = plan[i]
        val prev = plan.getOrNull(i - 1) ?: return true
        return prev.set != seg.set || prev.phase == Phase.SET_REST || prev.phase == Phase.READY
    }

    private fun isFirstOfRound(i: Int): Boolean {
        val seg = plan[i]
        val prev = plan.getOrNull(i - 1) ?: return true
        return prev.round != seg.round || prev.phase == Phase.SET_REST || prev.phase == Phase.READY
    }


    /** Cue played when `remaining` whole seconds are left in a countdown segment. */
    private fun countdownCues(seg: Segment, remaining: Int): Cue? {
        val d = seg.durationSeconds
        return when {
            remaining == 10 && d >= 30 -> Cue.Speak("10 seconds")
            remaining in 1..3 -> Cue.Beep
            d >= 60 && remaining == d / 2 -> Cue.Speak("Halfway")
            else -> null
        }
    }

    companion object {
        const val DEFAULT_READY_SECONDS = 10
        private const val TICK_SLACK_MS = 10L
        private const val IDLE_TICK_MS = 1000L
        private const val MIN_CLOSING_LAP_MS = 1000L
    }
}
