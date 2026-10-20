package com.baccours.kronos.ui.running

import com.baccours.kronos.model.Lap
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.model.UpNext
import com.baccours.kronos.model.formatDuration
import kotlin.math.abs

/** Display model for a logged lap with time difference delta. */
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
    /** Active segment progress fraction (null if open-ended). */
    val segmentProgress: Float? = null,
    /** Target progress fraction for 1-second animation interpolation. */
    val segmentProgressNext: Float? = null,
    /** Overall workout progress fraction (TIMER mode only). */
    val overallProgress: Float? = null,
    val totalRemaining: String? = null,
    val totalElapsed: String? = null,
    val timeCap: String? = null,
    val lapTime: String? = null,
    val next: UpNext? = null,
    /** Reverse-chronological list of laps in current set. */
    val laps: List<LapUi> = emptyList(),
    /** Summary results per set on completion (FOR_TIME mode). */
    val summaries: List<SetSummaryUi> = emptyList(),
    val finishedTotal: String = "00:00"
) {
    val isFinished get() = status == TimerStatus.FINISHED
    val isPaused get() = status == TimerStatus.PAUSED
    val isForTime get() = mode == TimerMode.FOR_TIME

    /** True if lap logging is active in FOR_TIME mode. */
    val canLap get() = isForTime && phase == Phase.WORK && !isFinished
}

/**
 * Maps [TimerState] to [RunningUiState], caching lap UI items and set summaries.
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

/** Converts laps to display models computing time deltas between consecutive laps. */
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
