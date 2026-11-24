package com.baccours.kronos.audio

import android.util.Log
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import java.util.concurrent.ExecutorService

/**
 * [Announcer] playing concatenated audio clips from a [ClipSource].
 * Skips the entire announcement and returns false if any required clip is missing.
 * Public methods are main-thread only; the background [worker] assembles the audio.
 *
 * Everything platform-specific is behind the interfaces in `AudioPorts.kt`; the production wiring
 * is the `ClipAnnouncer(context, focus)` factory in `AndroidAudio.kt`.
 */
class ClipAnnouncer internal constructor(
    private val focus: FocusGate,
    private val library: ClipSource,
    private val mainThread: MainThread,
    private val worker: ExecutorService,
    private val clips: ClipFactory
) : Announcer {

    // Playback state (main thread)

    private class Playing(val id: Int, val clip: PreparedClip) {
        lateinit var end: Runnable
    }

    private val queue = ArrayDeque<PreparedClip>()
    private var current: Playing? = null
    private var playCounter = 0

    /** Incremented by [stop]/interrupts to drop in-flight background assemblies (read by the worker). */
    @Volatile
    private var generation = 0
    private var inFlight = 0
    private val warned = HashSet<String>()
    private var released = false

    override val isSpeaking: Boolean
        get() = current != null || queue.isNotEmpty() || inFlight > 0

    override fun prepare(speeches: List<Speech>) {
        library.preload(ClipCatalog.clipsForAll(speeches))
    }

    override fun say(cue: Cue.Speak): Boolean {
        if (released) return false
        val names = ClipCatalog.clipsFor(cue)
        if (names.isEmpty()) return false
        // Skip if clips are missing or failed decoding recently.
        val unusable = names.filter { !library.isUsable(it) }
        if (unusable.isNotEmpty()) {
            // Log unusable clips once.
            unusable.filter { warned.add(it) }.takeIf { it.isNotEmpty() }
                ?.let { Log.w(TAG, "Not announcing \"${cue.text}\", unusable: ${it.joinToString()}") }
            // Don't let the previous announcement talk over the new segment.
            if (cue.interrupt) stop()
            return false
        }
        if (cue.interrupt) stopPlayback()
        if (!focus.request()) return false
        val gen = generation
        inFlight++
        // Sequential worker thread creates the clip and preserves clip order.
        // Voice starts after the segment buzzer so long phrases stay intelligible.
        val lead = if (cue.interrupt) LEAD_SILENCE else null
        worker.execute {
            if (gen != generation) return@execute // Superseded before it started.
            // Always report back, or inFlight stays > 0 and audio focus is never released.
            val prepared = try {
                val pcm = assemble(names, lead)
                if (pcm == null) Log.w(TAG, "Dropped \"${cue.text}\": a clip could not be decoded")
                pcm?.takeIf { it.isNotEmpty() && gen == generation }?.let(clips::create)
            } catch (e: Exception) {
                Log.e(TAG, "Dropped \"${cue.text}\": cannot prepare", e)
                null
            }
            mainThread.post { onPrepared(gen, prepared) }
        }
        return true
    }

    override fun stop() {
        stopPlayback()
        focus.releaseAfter(FOCUS_TAIL_MS)
    }

    override fun release() {
        released = true
        stopPlayback()
        worker.shutdownNow()
        // Shared ClipLibrary remains cached across workouts.
    }

    private fun assemble(names: List<String>, lead: ShortArray?): ShortArray? {
        val parts = names.map { library.pcm(it) ?: return null }
        return PcmUtil.concat(if (lead != null) listOf(lead) + parts else parts)
    }

    private fun onPrepared(gen: Int, prepared: PreparedClip?) {
        if (gen != generation) {
            // Discard the clip if playback was stopped during preparation.
            prepared?.let(::releaseClip)
            return
        }
        inFlight--
        if (prepared == null) {
            if (!isSpeaking) focus.releaseAfter(FOCUS_TAIL_MS)
            return
        }
        queue.addLast(prepared)
        if (current == null) playNext()
    }

    private fun playNext() {
        val clip = queue.removeFirstOrNull()
        if (clip == null) {
            if (!isSpeaking) focus.releaseAfter(FOCUS_TAIL_MS)
            return
        }
        val playing = Playing(++playCounter, clip)
        playing.end = Runnable { finish(playing.id) }
        current = playing
        if (!focus.request()) { // Focus lost since enqueue (e.g. a call started).
            finish(playing.id)
            return
        }
        try {
            clip.start {
                // Allow remaining audio frames to flush before finishing.
                mainThread.cancel(playing.end)
                mainThread.postDelayed(playing.end, OUTPUT_TAIL_MS)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot start playback", e)
            finish(playing.id)
            return
        }
        // Fallback timeout in case the end-of-clip notification never arrives.
        mainThread.postDelayed(playing.end, clip.frames * 1000L / SAMPLE_RATE + FALLBACK_MS)
    }

    private fun finish(id: Int) {
        val playing = current ?: return
        if (playing.id != id) return
        mainThread.cancel(playing.end)
        current = null
        releaseClip(playing.clip)
        playNext()
    }

    private fun stopPlayback() {
        generation++
        inFlight = 0
        queue.forEach { releaseClip(it) }
        queue.clear()
        current?.let {
            mainThread.cancel(it.end)
            releaseClip(it.clip)
        }
        current = null
    }

    private fun releaseClip(clip: PreparedClip) {
        try {
            clip.release()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot release AudioTrack", e)
        }
    }

    internal companion object {
        const val TAG = "ClipAnnouncer"
        const val FOCUS_TAIL_MS = AudioFocusController.TAIL_MS
        const val OUTPUT_TAIL_MS = 60L
        const val FALLBACK_MS = 500L

        const val SAMPLE_RATE = ClipLibrary.SAMPLE_RATE

        /** Silence before interrupting announcements (the segment buzzer plays meanwhile). */
        const val VOICE_LEAD_MS = 300
        val LEAD_SILENCE = ShortArray(SAMPLE_RATE * VOICE_LEAD_MS / 1000)
    }
}
