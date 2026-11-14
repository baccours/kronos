package com.baccours.kronos.model

enum class TimerStatus {
    NOT_STARTED, RUNNING, PAUSED, FINISHED
}

enum class Phase {
    READY, WORK, REST, SET_REST
}

/** Logged lap entry in FOR_TIME mode. */
data class Lap(
    val set: Int,
    val number: Int,
    val durationMs: Long
)

/** Preview of the upcoming workout segment. */
data class UpNext(
    val phase: Phase,
    val workIndex: Int,
    val set: Int,
    val seconds: Int
)

/** Immutable workout state snapshot. */
data class TimerState(
    val status: TimerStatus = TimerStatus.NOT_STARTED,
    val config: TimerConfig? = null,
    val phase: Phase = Phase.READY,
    /** Zero-based index of current segment. */
    val segmentIndex: Int = 0,
    val currentSet: Int = 1,
    /** Current round (TIMER) or active lap number (FOR_TIME). */
    val currentRound: Int = 1,
    /** 1-based index of active WORK task within round (0 if inapplicable). */
    val workIndex: Int = 0,
    /** Planned segment duration in seconds (0 = open-ended). */
    val segmentSeconds: Int = 0,
    val segmentElapsedMs: Long = 0,
    /** Seconds elapsed in current lap (FOR_TIME). */
    val lapElapsedSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    /** Total remaining workout seconds (TIMER mode). */
    val totalRemainingSeconds: Int? = null,
    val next: UpNext? = null,
    val laps: List<Lap> = emptyList()
) {
    val elapsedSeconds: Int get() = (segmentElapsedMs / 1000).toInt()
    val remainingSeconds: Int
        get() = if (segmentSeconds > 0) (segmentSeconds - elapsedSeconds).coerceAtLeast(0) else 0
}
