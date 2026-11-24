package com.baccours.kronos.audio

/**
 * Ducks external audio during cue playback. Must be called from the main thread.
 * Owns the "do we hold focus" state; the system calls are behind [FocusSystem].
 */
class AudioFocusController internal constructor(
    private val mainThread: MainThread,
    private val system: FocusSystem
) : FocusGate {

    /** Retains audio focus while true, overriding scheduled releases. */
    var isBusy: () -> Boolean = { false }

    /** Called when another app (e.g. an incoming call) takes focus. */
    var onLost: () -> Unit = {}

    private var hasFocus = false

    private val abandonRunnable = Runnable { if (!isBusy()) abandon() }

    companion object {
        /** Delay before releasing focus after sound finishes. */
        const val TAIL_MS = 300L
    }

    /** Returns false when focus is denied (e.g. during a call): stay silent then. */
    override fun request(): Boolean {
        mainThread.cancel(abandonRunnable)
        if (hasFocus) return true
        hasFocus = system.request(onLoss = ::focusLost)
        return hasFocus
    }

    /** Releases audio focus after [delayMs] if idle. */
    override fun releaseAfter(delayMs: Long) {
        mainThread.cancel(abandonRunnable)
        mainThread.postDelayed(abandonRunnable, delayMs)
    }

    /** Releases audio focus immediately. */
    fun release() {
        mainThread.cancel(abandonRunnable)
        abandon()
    }

    private fun focusLost() {
        release()
        onLost()
    }

    private fun abandon() {
        if (!hasFocus) return
        system.abandon()
        hasFocus = false
    }
}

/** Keeps focus while [announcer] speaks and silences it when another app takes focus. */
internal fun AudioFocusController.bindTo(announcer: Announcer) {
    isBusy = { announcer.isSpeaking }
    onLost = { announcer.stop() }
}
