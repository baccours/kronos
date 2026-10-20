package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import java.util.Locale

/**
 * Maps announcements to audio clip filenames in `assets/audio/`.
 * Formats numbers up to [MAX_NUMBER] using dedicated clips or composed number words.
 */
object ClipCatalog {

    const val DIR = "audio"
    const val EXTENSION = ".ogg"

    /** Maximum speakable number; higher values play only the label. */
    const val MAX_NUMBER = 999

    /** Maximum N with a dedicated audio clip (`workNN`, `setNN`, `roundNN`). */
    const val MAX_DEDICATED = 10

    private val UNITS = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen"
    )
    private val TENS = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private const val HUNDRED = "hundred"

    /** Fixed workout announcements. */
    val PHRASES: List<Speech> = listOf(
        Speech.GetReady, Speech.Go, Speech.Rest, Speech.RestBetweenSets,
        Speech.Halfway, Speech.TenSeconds, Speech.WellDone
    )

    /** Ordered clip names (without extension) for [cue]. */
    fun clipsFor(cue: Cue.Speak): List<String> = cue.parts.flatMap(::clipsFor)

    /** Unique clip names required for [speeches] in execution order. */
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

    /** Returns dedicated clip name `<label>NN` if available, or label plus number words. */
    private fun numbered(label: String, n: Int, firstDedicated: Int): List<String> =
        if (n in firstDedicated..MAX_DEDICATED) {
            listOf(String.format(Locale.ROOT, "%s%02d", label, n))
        } else {
            listOf(label) + number(n)
        }

    /** Spoken word components for 1..[MAX_NUMBER]. */
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
        in 1..19 -> listOf(UNITS[n - 1])
        else -> listOfNotNull(TENS[n / 10 - 2], UNITS.getOrNull(n % 10 - 1))
    }

    /** All potential clip names sorted alphabetically. */
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
