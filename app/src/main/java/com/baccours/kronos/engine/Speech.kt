package com.baccours.kronos.engine

/**
 * One thing the announcer says. The engine only describes *what* is said; how it is
 * rendered (recorded clips today, maybe text-to-speech later) is up to the Announcer.
 */
sealed interface Speech {
    data object GetReady : Speech
    data object Go : Speech
    data object Rest : Speech
    data object RestBetweenSets : Speech
    data object Halfway : Speech
    data object TenSeconds : Speech
    data object WellDone : Speech

    /** "Work", or "Work N" when [index] is above zero. */
    data class Work(val index: Int = 0) : Speech

    /** "Set N" */
    data class SetNumber(val number: Int) : Speech

    /** "Round N" */
    data class RoundNumber(val number: Int) : Speech
}

/** English rendering of a [Speech]: for logs, tests and any announcer that needs text. */
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
