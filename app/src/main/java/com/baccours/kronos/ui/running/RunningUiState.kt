package com.baccours.kronos.ui.running

import com.baccours.kronos.model.Lap
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.UpNext
import com.baccours.kronos.model.formatDuration
import kotlin.math.abs

/** A logged lap, ready to display. [delta] is the difference to the previous lap of the set. */
data class LapUi(
    val set: Int,
    val number: Int,
    val time: String,
    val delta: String? = null,
    val isFaster: Boolean = false
)

data class SetSummaryUi(
    val set: Int,
    val total: String,
    val laps: List<LapUi>
)

data class RunningUiState(
    val status: TimerStatus = TimerStatus.NOT_STARTED,
    val mode: TimerMode = TimerMode.TIMER,
    val phase: Phase = Phase.READY,
    val workIndex: Int = 0,
    val currentSet: Int = 1,
    val totalSets: Int = 1,
    val currentRound: Int = 1,
    val totalRounds: Int = 1,
    val isLastSet: Boolean = true,
    val displayTime: String = "00:00",
    val elapsedSeconds: Int = 0,
    val segmentIndex: Int = 0,
    /** Progress through the current segment, null for open-ended segments. */
    val segmentProgress: Float? = null,
    /** Where [segmentProgress] will be one second from now (for smooth animation). */
    val segmentProgressNext: Float? = null,
    /** Progress through the whole workout (TIMER mode), null otherwise. */
    val overallProgress: Float? = null,
    val totalRemaining: String? = null,
    val totalElapsed: String? = null,
    val timeCap: String? = null,
    val lapTime: String? = null,
    val next: UpNext? = null,
    /** Laps of the set in progress, newest first. */
    val laps: List<LapUi> = emptyList(),
    /** Per-set results, shown when the workout is finished (For Time). */
    val summaries: List<SetSummaryUi> = emptyList(),
    val finishedTotal: String = "00:00"
) {
    val isFinished get() = status == TimerStatus.FINISHED
    val isPaused get() = status == TimerStatus.PAUSED
    val isForTime get() = mode == TimerMode.FOR_TIME

    /** The "ROUND" lap button is available during a For Time set. */
    val canLap get() = isForTime && phase == Phase.WORK && !isFinished
}

/**
 * Turns the engine's [TimerState] into what the running screen shows. Called once a second, so
 * the lap rows (which only change when a lap is logged) are rebuilt only when the engine hands
 * over a different laps list, and the per-set results only once the workout is finished.
 */
class RunningUiStateMapper {
    private var currentLapsSource: List<Lap>? = null
    private var currentLapsSet = -1
    private var currentLapsUi: List<LapUi> = emptyList()

    private var summariesSource: List<Lap>? = null
    private var summariesUi: List<SetSummaryUi> = emptyList()

    fun map(state: TimerState): RunningUiState = with(state) {
        val cfg = config ?: return RunningUiState(status = status)
        val isForTime = cfg.mode == TimerMode.FOR_TIME
        val countsUp = isForTime && phase == Phase.WORK

        val progress = if (segmentSeconds > 0) {
            elapsedSeconds.toFloat() / segmentSeconds
        } else null
        val progressNext = if (segmentSeconds > 0) {
            (elapsedSeconds + 1).coerceAtMost(segmentSeconds).toFloat() / segmentSeconds
        } else null

        val overall = totalRemainingSeconds?.let { remaining ->
            val total = totalElapsedSeconds + remaining
            if (total > 0) totalElapsedSeconds.toFloat() / total else 0f
        }

        return RunningUiState(
            status = status,
            mode = cfg.mode,
            phase = phase,
            workIndex = workIndex,
            currentSet = currentSet,
            totalSets = cfg.sets,
            currentRound = currentRound,
            totalRounds = cfg.rounds,
            isLastSet = currentSet >= cfg.sets,
            displayTime = (if (countsUp) elapsedSeconds else remainingSeconds).formatDuration(),
            elapsedSeconds = elapsedSeconds,
            segmentIndex = segmentIndex,
            segmentProgress = progress,
            segmentProgressNext = progressNext,
            overallProgress = overall,
            totalRemaining = totalRemainingSeconds?.formatDuration(),
            totalElapsed = if (isForTime) totalElapsedSeconds.formatDuration() else null,
            timeCap = if (isForTime && phase == Phase.WORK && cfg.timeCap > 0) {
                cfg.timeCap.formatDuration()
            } else null,
            lapTime = if (countsUp) lapElapsedSeconds.formatDuration() else null,
            next = next,
            laps = currentSetLapsUi(laps, currentSet),
            summaries = if (isForTime && status == TimerStatus.FINISHED) summariesUi(laps) else emptyList(),
            finishedTotal = totalElapsedSeconds.formatDuration()
        )
    }

    private fun currentSetLapsUi(laps: List<Lap>, set: Int): List<LapUi> {
        if (laps !== currentLapsSource || set != currentLapsSet) {
            currentLapsUi = laps.filter { it.set == set }.toLapUi().asReversed()
            currentLapsSource = laps
            currentLapsSet = set
        }
        return currentLapsUi
    }

    private fun summariesUi(laps: List<Lap>): List<SetSummaryUi> {
        if (laps !== summariesSource) {
            summariesUi = laps.groupBy { it.set }.map { (set, setLaps) ->
                SetSummaryUi(
                    set = set,
                    total = (setLaps.sumOf { it.durationMs } / 1000).toInt().formatDuration(),
                    laps = setLaps.toLapUi()
                )
            }
            summariesSource = laps
        }
        return summariesUi
    }
}

/** Converts laps of a single set into display rows with the delta to the previous lap. */
private fun List<Lap>.toLapUi(): List<LapUi> = mapIndexed { i, lap ->
    val previous = getOrNull(i - 1)
    val diffSeconds = previous?.let { (lap.durationMs - it.durationMs) / 1000 }
    LapUi(
        set = lap.set,
        number = lap.number,
        time = (lap.durationMs / 1000).toInt().formatDuration(),
        delta = diffSeconds?.takeIf { it != 0L }?.let {
            val sign = if (it < 0) "−" else "+"
            sign + abs(it).toInt().formatDuration()
        },
        isFaster = (diffSeconds ?: 0L) < 0
    )
}
