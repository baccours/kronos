package com.baccours.kronos.engine

/**
 * Spoken cue directive emitted by the workout engine.
 */
sealed interface Speech {
    data object GetReady : Speech
    data object Go : Speech
    data object Rest : Speech
    data object RestBetweenSets : Speech
    data object Halfway : Speech
    data object TenSeconds : Speech
    data object WellDone : Speech

    /** Work phase cue, numbered if [index] > 0. */
    data class Work(val index: Int = 0) : Speech

    /** Set number cue. */
    data class SetNumber(val number: Int) : Speech

    /** Round number cue. */
    data class RoundNumber(val number: Int) : Speech
}

/** English text representation of [Speech]. */
val Speech.english: String
    get() = when (this) {
        Speech.GetReady -> "Get ready"
        Speech.Go -> "Go"
        Speech.Rest -> "Rest"
        Speech.RestBetweenSets -> "Rest between sets"
        Speech.Halfway -> "Halfway"
        Speech.TenSeconds -> "10 seconds"
        Speech.WellDone -> "Well done"
        is Speech.Work -> if (index > 0) "Work $index" else "Work"
        is Speech.SetNumber -> "Set $number"
        is Speech.RoundNumber -> "Round $number"
    }
