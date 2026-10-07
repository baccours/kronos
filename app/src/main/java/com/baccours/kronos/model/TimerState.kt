package com.baccours.kronos.model

enum class TimerStatus {
    NOT_STARTED, RUNNING, PAUSED, FINISHED
}

enum class Phase {
    READY, WORK, REST, SET_REST
}

/** One logged lap of a "For Time" set. */
data class Lap(
    val set: Int,
    val number: Int,
    val durationMs: Long
)

/** Description of the segment that follows the current one. */
data class UpNext(
    val phase: Phase,
    val workIndex: Int,
    val set: Int,
    val seconds: Int
)

/** Immutable snapshot published by the engine and consumed by the UI / notification. */
data class TimerState(
    val status: TimerStatus = TimerStatus.NOT_STARTED,
    val config: TimerConfig? = null,
    val phase: Phase = Phase.READY,
    /** Index of the current segment in the plan; changes whenever the phase changes. */
    val segmentIndex: Int = 0,
    val currentSet: Int = 1,
    /** TIMER: current round. FOR_TIME: number of the lap in progress. */
    val currentRound: Int = 1,
    /** 1-based index of the WORK task within its round, 0 when not applicable. */
    val workIndex: Int = 0,
    /** Planned length of the current segment, 0 when open-ended (For Time without cap). */
    val segmentSeconds: Int = 0,
    val segmentElapsedMs: Long = 0,
    val elapsedSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    /** For Time: seconds since the last lap. */
    val lapElapsedSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    /** TIMER mode only. */
    val totalRemainingSeconds: Int? = null,
    val next: UpNext? = null,
    val laps: List<Lap> = emptyList()
)
