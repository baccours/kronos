package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import java.util.Locale

/**
 * Which recorded clips make up each announcement. Pure Kotlin (no Android), so it can be unit-tested.
 *
 * Clips are `assets/audio/<name>.ogg`. Names are lower case:
 *  - phrases:  `get_ready`, `go`, `rest`, `rest_between_sets`, `halfway`, `ten_seconds`, `well_done`
 *  - "Work N": `work01`..`workNN` (dedicated clips, N up to [MAX_DEDICATED])
 *  - "Set N":  `set02`..`setNN` (dedicated clips; "Set 1" is never spoken)
 *  - "Round N": `round02`..`roundNN` (dedicated clips; "Round 1" is never spoken)
 *  - above [MAX_DEDICATED]: the label (`work`, `set`, `round`) followed by number words
 *  - number words: `one`..`twenty`, `thirty`..`ninety`, `hundred`
 *
 * Number words are composed like English: 25 is `twenty` `five`, 100 is `one` `hundred`,
 * 125 is `one` `hundred` `twenty` `five`. Numbers go up to [MAX_NUMBER]; above it only the label is said.
 */
object ClipCatalog {

    const val DIR = "audio"
    const val EXTENSION = ".ogg"

    /** Largest number that is spoken. Above it only the label ("Work", "Set", "Round") is said. */
    const val MAX_NUMBER = 999

    /** Highest N that has its own `workNN`, `setNN` and `roundNN` clip. */
    const val MAX_DEDICATED = 10

    private val UNITS = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen", "twenty"
    )
    private val TENS = listOf("thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private const val HUNDRED = "hundred"

    /** Announcements that do not depend on the workout's configuration. */
    val PHRASES: List<Speech> = listOf(
        Speech.GetReady, Speech.Go, Speech.Rest, Speech.RestBetweenSets,
        Speech.Halfway, Speech.TenSeconds, Speech.WellDone
    )

    /** Clip names (no extension) to play, in order, for [cue]. */
    fun clipsFor(cue: Cue.Speak): List<String> = cue.parts.flatMap(::clipsFor)

    /** Distinct clip names for all of [speeches], in the order they are first needed. */
    fun clipsForAll(speeches: Iterable<Speech>): List<String> =
        speeches.flatMap { clipsFor(it) }.distinct()

    fun clipsFor(speech: Speech): List<String> = when (speech) {
        Speech.GetReady -> listOf("get_ready")
        Speech.Go -> listOf("go")
        Speech.Rest -> listOf("rest")
        Speech.RestBetweenSets -> listOf("rest_between_sets")
        Speech.Halfway -> listOf("halfway")
        Speech.TenSeconds -> listOf("ten_seconds")
        Speech.WellDone -> listOf("well_done")
        is Speech.Work -> numbered("work", speech.index, firstDedicated = 1)
        is Speech.SetNumber -> numbered("set", speech.number, firstDedicated = 2)
        is Speech.RoundNumber -> numbered("round", speech.number, firstDedicated = 2)
    }

    /**
     * `<label>NN` when there is a dedicated clip for [n], otherwise the label followed by number words.
     * [firstDedicated] is the lowest N that was recorded (Set 1 and Round 1 are never spoken).
     */
    private fun numbered(label: String, n: Int, firstDedicated: Int): List<String> =
        if (n in firstDedicated..MAX_DEDICATED) {
            listOf(String.format(Locale.ROOT, "%s%02d", label, n))
        } else {
            listOf(label) + number(n)
        }

    /** Number words for 1..[MAX_NUMBER]; empty for anything else. */
    private fun number(n: Int): List<String> {
        if (n !in 1..MAX_NUMBER) return emptyList()
        val words = ArrayList<String>()
        if (n >= 100) {
            words += UNITS[n / 100 - 1]
            words += HUNDRED
        }
        words += below100(n % 100)
        return words
    }

    private fun below100(n: Int): List<String> = when (n) {
        0 -> emptyList()
        in 1..20 -> listOf(UNITS[n - 1])
        else -> {
            val tens = if (n / 10 == 2) "twenty" else TENS[n / 10 - 3]
            listOfNotNull(tens, UNITS.getOrNull(n % 10 - 1)) // no units word for 30, 40, ...
        }
    }

    /** Every clip name (no extension) the app may ever ask for, sorted. */
    val allClips: List<String> by lazy {
        val all = ArrayList<Speech>(PHRASES)
        for (n in 1..MAX_NUMBER) all += Speech.Work(n)
        for (n in 2..MAX_NUMBER) {
            all += Speech.SetNumber(n)
            all += Speech.RoundNumber(n)
        }
        all.flatMap(::clipsFor).toSortedSet().toList()
    }
}
