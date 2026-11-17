package com.baccours.kronos.engine

import com.baccours.kronos.model.Lap
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.UpNext

data class EngineResult(
    val state: TimerState,
    val cues: List<Cue> = emptyList()
)

/**
 * Pure, clock-injected workout state machine.
 *
 * Segments (ready, work, rest, etc.) are pre-planned without zero-length items; the plan
 * always starts with a READY segment, so it is never empty.
 * TIMER mode counts down without drift accumulation; FOR_TIME mode counts up with
 * lap logging and optional set/total time caps.
 */
class TimerEngine(private val config: TimerConfig) {
    private val plan = WorkoutPlan(config)

    private val isForTime = config.mode == TimerMode.FOR_TIME

    /** Overall time cap, only for multi-set FOR_TIME workouts (0 = disabled). */
    private val totalCapSeconds = config.effectiveTotalCapSeconds
    private val totalCapMs = totalCapSeconds * 1000L

    private val cuePolicy = CuePolicy(plan, isForTime, totalCapMs)

    var status: TimerStatus = TimerStatus.NOT_STARTED
        private set

    private var index = 0
    private var segmentStartMs = 0L
    private var lapStartMs = 0L
    private var pausedAtMs = 0L
    private var completedMs = 0L
    private var lastElapsedSecond = 0

    /** Planned seconds of the segments after the current one (TIMER mode; READY counts for nothing). */
    private var secondsAfter = config.totalDurationSeconds

    /** Start cues of a segment skipped to while paused; played on resume. */
    private var pendingCues: List<Cue> = emptyList()
    private val laps = ArrayList<Lap>()

    /** Cached immutable copy of [laps] for O(1) snapshot updates. */
    private var lapsSnapshot: List<Lap> = emptyList()
    private val lapCountBySet = HashMap<Int, Int>()

    fun start(now: Long): EngineResult {
        index = 0
        segmentStartMs = now
        lapStartMs = now
        completedMs = 0
        lastElapsedSecond = 0
        secondsAfter = config.totalDurationSeconds
        pendingCues = emptyList()
        laps.clear()
        lapCountBySet.clear()
        lapsSnapshot = emptyList()
        status = TimerStatus.RUNNING
        return EngineResult(snapshot(now), cuePolicy.startCues(index))
    }

    fun tick(now: Long): EngineResult {
        if (status != TimerStatus.RUNNING) return EngineResult(snapshot(now))
        val cues = ArrayList<Cue>()
        while (true) {
            val seg = plan[index]
            val d = seg.durationSeconds
            val elapsedMs = now - segmentStartMs
            val capLeftMs = totalCapLeftMs(seg)
            if (capLeftMs != null && elapsedMs >= capLeftMs) {
                // Enforce overall time cap cutoff.
                val boundary = segmentStartMs + capLeftMs
                if (boundary > segmentStartMs) completeSegment(boundary)
                finish(cues)
                break
            }
            if (d > 0 && elapsedMs >= d * 1000L) {
                // Transition on planned segment boundary.
                val boundary = segmentStartMs + d * 1000L
                completeSegment(boundary)
                cues.clear() // Announce only current landing segment
                if (index >= plan.lastIndex) {
                    finish(cues)
                    break
                }
                advance()
                segmentStartMs = boundary
                lapStartMs = boundary
                lastElapsedSecond = 0
                cues += cuePolicy.startCues(index)
                continue
            }
            val elapsedSecond = (elapsedMs / 1000).toInt()
            // Only the current second is announced: after a stall, older cues would be stale.
            if ((d > 0 || capLeftMs != null) && elapsedSecond > lastElapsedSecond) {
                val cue = (if (d > 0) cuePolicy.countdownCues(seg, d - elapsedSecond) else null)
                    ?: capLeftMs?.let { cuePolicy.totalCapCue(it - elapsedSecond * 1000L) }
                cue?.let { cues += it }
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
        var cues = emptyList<Cue>()
        if (status == TimerStatus.PAUSED) {
            val shift = now - pausedAtMs
            segmentStartMs += shift
            lapStartMs += shift
            status = TimerStatus.RUNNING
            cues = pendingCues
            pendingCues = emptyList()
        }
        return EngineResult(snapshot(now), cues)
    }

    /**
     * Advances to the next segment immediately (logs closing lap in FOR_TIME mode).
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
            advance()
            segmentStartMs = t
            lapStartMs = t
            lastElapsedSecond = 0
            // While paused, announce on resume instead; finishing still announces the end.
            val start = cuePolicy.startCues(index)
            if (status == TimerStatus.RUNNING) cues += start else pendingCues = start
        }
        return EngineResult(snapshot(now), cues)
    }

    /** Logs a lap in FOR_TIME mode. */
    fun lap(now: Long): EngineResult {
        val seg = plan.getOrNull(index)
        if (status != TimerStatus.RUNNING || seg == null || !isLapSegment(seg)) {
            return EngineResult(snapshot(now))
        }
        addLap(seg.set, now - lapStartMs)
        lapStartMs = now
        return EngineResult(snapshot(now), listOf(Cue.Beep))
    }

    /** Delay until the next second boundary. */
    fun millisUntilNextTick(now: Long): Long {
        if (status != TimerStatus.RUNNING) return IDLE_TICK_MS
        val elapsed = (now - segmentStartMs).coerceAtLeast(0)
        val toNextSecond = (1000 - (elapsed % 1000)) + TICK_SLACK_MS
        // Align tick with total cap expiration.
        val capLeft = plan.getOrNull(index)?.let { totalCapLeftMs(it) } ?: return toNextSecond
        val toCap = capLeft - elapsed
        return if (toCap > 0) minOf(toNextSecond, toCap + TICK_SLACK_MS) else TICK_SLACK_MS
    }

    fun snapshot(now: Long): TimerState {
        val t = reference(now)
        val seg = plan[index]
        val finished = status == TimerStatus.FINISHED
        val d = seg.durationSeconds
        val elapsedMs = if (finished) 0L else (t - segmentStartMs).coerceAtLeast(0)
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        val remaining = if (d > 0) (d - elapsedSeconds).coerceAtLeast(0) else 0
        val setLaps = lapCountBySet[seg.set] ?: 0
        val inTimedProgress = seg.phase != Phase.READY && !finished
        val totalElapsedMs = completedMs + if (inTimedProgress) elapsedMs else 0L

        return TimerState(
            status = status,
            config = config,
            phase = seg.phase,
            segmentIndex = index,
            currentSet = seg.set,
            currentRound = seg.round,
            currentLap = setLaps + 1,
            workIndex = seg.workIndex,
            segmentSeconds = d,
            segmentElapsedMs = elapsedMs,
            lapElapsedSeconds = if (isLapSegment(seg) && !finished) {
                ((t - lapStartMs).coerceAtLeast(0) / 1000).toInt()
            } else 0,
            totalElapsedSeconds = (totalElapsedMs / 1000).toInt(),
            totalRemainingSeconds = when {
                finished -> if (isForTime && totalCapMs <= 0) null else 0
                totalCapMs > 0 -> (totalCapSeconds - (totalElapsedMs / 1000).toInt())
                    .coerceAtLeast(0)
                isForTime -> null
                else -> (if (seg.phase == Phase.READY) 0 else remaining) + secondsAfter
            },
            next = if (finished) null else plan.getOrNull(index + 1)?.let {
                UpNext(it.phase, it.workIndex, it.set, it.durationSeconds)
            },
            laps = lapsSnapshot
        )
    }

    /**
     * Returns all unique speech directives required for preloading.
     */
    fun speechNeeded(): List<Speech> = cuePolicy.speechNeeded(config.sets)

    /** Reference timestamp accounting for pause state. */
    private fun reference(now: Long): Long =
        if (status == TimerStatus.PAUSED) pausedAtMs else now

    /** Remaining cap time in milliseconds for [seg], or null if un-capped. */
    private fun totalCapLeftMs(seg: Segment): Long? =
        if (totalCapMs > 0 && seg.phase != Phase.READY) {
            (totalCapMs - completedMs).coerceAtLeast(0)
        } else null

    private fun isLapSegment(seg: Segment) = isForTime && seg.phase == Phase.WORK

    private fun addLap(set: Int, durationMs: Long) {
        val number = (lapCountBySet[set] ?: 0) + 1
        lapCountBySet[set] = number
        laps += Lap(set = set, number = number, durationMs = durationMs.coerceAtLeast(0))
        lapsSnapshot = laps.toList()
    }

    /** Finalizes current segment duration and records closing lap if applicable. */
    private fun completeSegment(endMs: Long) {
        val seg = plan[index]
        if (isLapSegment(seg)) {
            val sinceLap = endMs - lapStartMs
            val hasLaps = (lapCountBySet[seg.set] ?: 0) > 0
            // Ignore sub-second ghost lap on finish.
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
        cues += Cue.Speak(Speech.WellDone, interrupt = true)
    }

    /** Moves to the next segment. */
    private fun advance() {
        index++
        secondsAfter -= plan[index].durationSeconds
    }

    companion object {
        private const val TICK_SLACK_MS = 10L
        private const val IDLE_TICK_MS = 1000L
        private const val MIN_CLOSING_LAP_MS = 1000L
    }
}
