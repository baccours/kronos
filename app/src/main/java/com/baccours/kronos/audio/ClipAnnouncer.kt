package com.baccours.kronos.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [Announcer] playing concatenated audio clips via [ClipLibrary] and [AudioTrack].
 * Skips the entire announcement and returns false if any required clip is missing.
 * Public methods are main-thread only; background worker handles audio decoding.
 */
class ClipAnnouncer(
    context: Context,
    private val focus: AudioFocusController,
    private val library: ClipLibrary = ClipLibrary.get(context)
) : Announcer {

    private val handler = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "clip-announcer").apply { isDaemon = true }
    }

    // Playback state (main thread)

    /** Announcement ready for playback with PCM written to [track]. */
    private class Prepared(val track: AudioTrack, val frames: Int)

    private class Playing(val id: Int, val track: AudioTrack, val frames: Int) {
        lateinit var end: Runnable
    }

    private val queue = ArrayDeque<Prepared>()
    private var current: Playing? = null
    private var playCounter = 0

    /** Increment by [stop] to drop in-flight background assemblies. */
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
            return false
        }
        if (cue.interrupt) stopPlayback()
        focus.request()
        val gen = generation
        inFlight++
        // Sequential worker thread creates AudioTrack and preserves clip order.
        worker.execute {
            val pcm = assemble(names)
            if (pcm == null) Log.w(TAG, "Dropped \"${cue.text}\": a clip could not be decoded")
            val prepared = pcm?.takeIf { it.isNotEmpty() }?.let(::buildTrack)
            handler.post { onPrepared(gen, prepared) }
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

    private fun assemble(names: List<String>): ShortArray? {
        val parts = names.map { library.pcm(it) ?: return null }
        return PcmUtil.concat(parts)
    }

    private fun onPrepared(gen: Int, prepared: Prepared?) {
        if (gen != generation) {
            // Discard track if playback was stopped during preparation.
            prepared?.let { releaseTrack(it.track) }
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
        val prepared = queue.removeFirstOrNull()
        if (prepared == null) {
            if (!isSpeaking) focus.releaseAfter(FOCUS_TAIL_MS)
            return
        }
        val track = prepared.track
        val playing = Playing(++playCounter, track, prepared.frames)
        playing.end = Runnable { finish(playing.id) }
        current = playing
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                // Allow remaining audio frames to flush before finishing.
                handler.removeCallbacks(playing.end)
                handler.postDelayed(playing.end, OUTPUT_TAIL_MS)
            }

            override fun onPeriodicNotification(t: AudioTrack?) = Unit
        }, handler)
        track.notificationMarkerPosition = playing.frames
        focus.request()
        try {
            track.play()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot start playback", e)
            finish(playing.id)
            return
        }
        // Fallback timeout in case notification marker fails.
        handler.postDelayed(playing.end, playing.frames * 1000L / SAMPLE_RATE + FALLBACK_MS)
    }

    private fun finish(id: Int) {
        val playing = current ?: return
        if (playing.id != id) return
        handler.removeCallbacks(playing.end)
        current = null
        releaseTrack(playing.track)
        playNext()
    }

    private fun stopPlayback() {
        generation++
        inFlight = 0
        queue.forEach { releaseTrack(it.track) }
        queue.clear()
        current?.let {
            handler.removeCallbacks(it.end)
            releaseTrack(it.track)
        }
        current = null
    }

    /** Builds a static AudioTrack from [pcm] on the worker thread. */
    private fun buildTrack(pcm: ShortArray): Prepared? =
        createStaticTrack(pcm, attributes)?.let { Prepared(it, pcm.size) }

    private fun releaseTrack(track: AudioTrack) {
        try {
            track.release()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot release AudioTrack", e)
        }
    }

    private companion object {
        const val TAG = "ClipAnnouncer"
        const val FOCUS_TAIL_MS = AudioFocusController.TAIL_MS
        const val OUTPUT_TAIL_MS = 60L
        const val FALLBACK_MS = 500L

        const val SAMPLE_RATE = ClipLibrary.SAMPLE_RATE
    }
}
