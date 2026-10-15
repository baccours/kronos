package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech

/**
 * Says the engine's spoken cues out loud. [ClipAnnouncer] plays pre-recorded clips; a
 * text-to-speech implementation (or a fallback that tries clips first, then TTS) can be
 * plugged in behind the same interface. Must be used from the main thread.
 */
interface Announcer {

    /** True while something is playing or still queued. */
    val isSpeaking: Boolean

    /**
     * Queues [cue] after what is already being said, or replaces it when [Cue.Speak.interrupt].
     * Returns false, having played nothing, when the cue could not be announced (e.g. a clip
     * is missing), so that a fallback announcer can take over.
     */
    fun say(cue: Cue.Speak): Boolean

    /**
     * Hint that [speeches] will be said soon, in this order, so the announcer can get ready
     * (e.g. decode clips) before they are needed. Safe to ignore, which is the default.
     */
    fun prepare(speeches: List<Speech>) {}

    /** Silences everything, including what is queued. */
    fun stop()

    fun release()
}
