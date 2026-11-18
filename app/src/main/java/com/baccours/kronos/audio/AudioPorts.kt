package com.baccours.kronos.audio

/**
 * Seams between the audio logic and the Android framework.
 *
 * [ClipAnnouncer] and [AudioFocusController] hold the timing- and concurrency-sensitive logic
 * (generation counter, queue, focus ownership). They talk to Android only through these interfaces,
 * so unit tests can drive them with deterministic fakes. The Android implementations are in
 * `AndroidAudio.kt` and stay deliberately thin.
 */

/** Runs work on the main thread. Stands in for `android.os.Handler`. */
internal interface MainThread {
    fun post(task: Runnable)
    fun postDelayed(task: Runnable, delayMs: Long)

    /** Removes every pending occurrence of [task]. */
    fun cancel(task: Runnable)
}

/** Decoded clips, as [ClipAnnouncer] needs them. Implemented by [ClipLibrary]. */
internal interface ClipSource {
    fun isUsable(name: String): Boolean

    /** Blocking. Null if the clip cannot be decoded. Called from the worker thread. */
    fun pcm(name: String): ShortArray?
    fun preload(names: Collection<String>)
}

/** An announcement built and ready to play. Wraps a static `AudioTrack` on Android. */
internal interface PreparedClip {
    val frames: Int

    /**
     * Starts playback. [onLastFrame] is called on the main thread once the last frame has been played.
     * Throws [IllegalStateException] if playback cannot start.
     */
    fun start(onLastFrame: () -> Unit)

    fun release()
}

/** Builds a [PreparedClip] from PCM. Called on the worker thread. Returns null on failure. */
internal fun interface ClipFactory {
    fun create(pcm: ShortArray): PreparedClip?
}

/** Audio focus as [ClipAnnouncer] uses it. Implemented by [AudioFocusController]. */
internal interface FocusGate {
    /** False when focus is denied (e.g. during a call): stay silent then. */
    fun request(): Boolean
    fun releaseAfter(delayMs: Long)
}

/** The system side of audio focus (`AudioManager`). */
internal interface FocusSystem {
    /**
     * Requests transient focus that lets other apps duck. Returns true if granted.
     * [onLoss] is called on the main thread when another app takes the focus, permanently or not.
     */
    fun request(onLoss: () -> Unit): Boolean

    fun abandon()
}
