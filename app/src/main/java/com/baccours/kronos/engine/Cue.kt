package com.baccours.kronos.engine

/** Audio action directive emitted by the engine. */
sealed interface Cue {
    /** Sequential speech announcement cue. */
    data class Speak(val parts: List<Speech>, val interrupt: Boolean = false) : Cue {
        constructor(vararg parts: Speech, interrupt: Boolean = false) : this(parts.toList(), interrupt)

        /** Formatted English text of all speech parts. */
        val text: String get() = parts.joinToString(". ") { it.english }
    }
    data object Beep : Cue
    data object StartBuzzer : Cue
    data object Celebration : Cue
}
