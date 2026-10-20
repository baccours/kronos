package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech

/**
 * Speaks workout audio cues. Must be called from the main thread.
 */
interface Announcer {

    /** True while audio is playing or queued. */
    val isSpeaking: Boolean

    /**
     * Queues [cue], or interrupts if [Cue.Speak.interrupt] is set. Returns false if unannounced.
     */
    fun say(cue: Cue.Speak): Boolean

    /**
     * Optional hint to preload [speeches] before playback.
     */
    fun prepare(speeches: List<Speech>) {}

    /** Silences active and queued audio. */
    fun stop()

    fun release()
}
