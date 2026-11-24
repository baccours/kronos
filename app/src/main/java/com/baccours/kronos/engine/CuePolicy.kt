package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase

/** Says [speech] when [remainingFor] (given the segment length) seconds are left, in segments of [minSeconds]+. */
private class CountdownSpeech(
    val speech: Speech,
    val minSeconds: Int,
    val remainingFor: (segmentSeconds: Int) -> Int
)

/**
 * Which cues the workout plays, and when. Pure. [speechNeeded] derives the preload list from
 * the same rules, so the two cannot drift apart.
 */
internal class CuePolicy(
    private val plan: WorkoutPlan,
    private val isForTime: Boolean,
    private val totalCapMs: Long
) {
    fun startCues(i: Int): List<Cue> {
        val seg = plan[i]
        val setIntro: List<Speech> =
            if (seg.set > 1 && isFirstOfSet(i)) listOf(Speech.SetNumber(seg.set)) else emptyList()
        val roundIntro: List<Speech> =
            if (seg.round > 1 && isFirstOfRound(i)) listOf(Speech.RoundNumber(seg.round)) else emptyList()
        return when (seg.phase) {
            Phase.READY -> listOf(Cue.Speak(Speech.GetReady))
            Phase.WORK -> listOf(
                Cue.StartBuzzer,
                // Interrupt active audio on segment start to remain synchronized with UI.
                Cue.Speak(
                    if (isForTime) setIntro + Speech.Go
                    else setIntro + roundIntro + Speech.Work(seg.workIndex),
                    interrupt = true
                )
            )
            Phase.REST -> listOf(
                Cue.StartBuzzer,
                Cue.Speak(setIntro + roundIntro + Speech.Rest, interrupt = true)
            )
            Phase.SET_REST -> listOf(
                Cue.StartBuzzer,
                Cue.Speak(listOf(Speech.RestBetweenSets), interrupt = true)
            )
        }
    }

    /** Returns countdown warning cue for remaining segment seconds. */
    fun countdownCues(seg: Segment, remaining: Int): Cue? {
        if (remaining in 1..LAST_BEEPS) return Cue.Beep
        val d = seg.durationSeconds
        return COUNTDOWN_SPEECH
            .firstOrNull { d >= it.minSeconds && remaining == it.remainingFor(d) }
            ?.let { Cue.Speak(it.speech) }
    }

    /** Returns warning cue when approaching total time cap limit. */
    fun totalCapCue(leftMs: Long): Cue? {
        if (leftMs <= 0) return null
        val remaining = ((leftMs + 999) / 1000).toInt()
        return when (remaining) {
            10 if totalCapMs >= 30_000L -> Cue.Speak(Speech.TenSeconds)
            in 1..3 -> Cue.Beep
            else -> null
        }
    }

    /** All unique speech directives required for preloading, for a workout of [sets] sets. */
    fun speechNeeded(sets: Int): List<Speech> {
        val out = LinkedHashSet<Speech>()
        fun add(cue: Cue?) {
            if (cue is Cue.Speak) out += cue.parts
        }
        fun scan(i: Int) {
            startCues(i).forEach(::add)
            val seconds = plan[i].durationSeconds
            for (rule in COUNTDOWN_SPEECH) if (seconds >= rule.minSeconds) out += rule.speech
        }
        // Set 1 (and the rest after it) has every phrase except the set numbers,
        // which are spoken at the first segment of each later set.
        for (i in 0..minOf(plan.lastIndex, plan.stride)) scan(i)
        for (set in 2..sets) {
            val first = 1 + (set - 1) * plan.stride
            if (first <= plan.lastIndex) scan(first)
        }
        add(totalCapCue(10_000L))
        out += Speech.WellDone
        return out.toList()
    }

    private fun isFirstOfSet(i: Int): Boolean {
        val seg = plan[i]
        val prev = plan.getOrNull(i - 1) ?: return true
        return prev.set != seg.set || prev.phase == Phase.SET_REST || prev.phase == Phase.READY
    }

    private fun isFirstOfRound(i: Int): Boolean {
        val seg = plan[i]
        val prev = plan.getOrNull(i - 1) ?: return true
        return prev.round != seg.round || prev.phase == Phase.SET_REST || prev.phase == Phase.READY
    }

    private companion object {
        const val LAST_BEEPS = 3

        /** Spoken countdown cues; also the source of the clips preloaded by [speechNeeded]. */
        val COUNTDOWN_SPEECH = listOf(
            CountdownSpeech(Speech.TenSeconds, minSeconds = 30) { 10 },
            CountdownSpeech(Speech.Halfway, minSeconds = 60) { it / 2 }
        )
    }
}
