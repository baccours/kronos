package com.baccours.kronos.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [Announcer] that plays recorded clips from `assets/audio/` (see [ClipCatalog] for the names).
 *
 * Decoded clips come from the process-wide [ClipLibrary]. An announcement is the PCM of its clips
 * concatenated, so the words follow each other with no gap, and it is played through one
 * [AudioTrack]; both are prepared on a background thread so the main thread only calls play().
 * If any clip of an announcement is missing, the whole announcement is skipped (a lone
 * "twenty" would be worse than silence) and [say] returns false.
 *
 * Public methods must be called from the main thread; decoding runs on background threads.
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

    // --- Playback state (main thread only)

    /** An announcement ready to play: its PCM is already written to [track]. */
    private class Prepared(val track: AudioTrack, val frames: Int)

    private class Playing(val id: Int, val track: AudioTrack, val frames: Int) {
        lateinit var end: Runnable
    }

    private val queue = ArrayDeque<Prepared>()
    private var current: Playing? = null
    private var playCounter = 0

    /** Bumped by [stop]: announcements still being assembled when it happens are dropped. */
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
        val missing = names.filter { !library.has(it) }
        if (missing.isNotEmpty()) {
            // Log each missing clip once, not on every announcement.
            missing.filter { warned.add(it) }.takeIf { it.isNotEmpty() }
                ?.let { Log.w(TAG, "Not announcing \"${cue.text}\", missing: ${it.joinToString()}") }
            return false
        }
        if (cue.interrupt) stopPlayback()
        focus.request()
        val gen = generation
        inFlight++
        // A single worker keeps announcements in order, even when some clips still need decoding.
        // It also creates the AudioTrack, so the main thread only has to call play().
        worker.execute {
            val prepared = assemble(names)?.takeIf { it.isNotEmpty() }?.let(::buildTrack)
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
        // The ClipLibrary is shared and stays alive, with its decoded clips, for the next workout.
    }

    // ------------------------------------------------------------------ assembling (background)

    private fun assemble(names: List<String>): ShortArray? {
        val parts = names.map { library.pcm(it) ?: return null }
        return PcmUtil.concat(parts)
    }

    // ------------------------------------------------------------------ playback (main thread)

    private fun onPrepared(gen: Int, prepared: Prepared?) {
        if (gen != generation) {
            // Stopped in the meantime: nobody will play this track.
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
                // All frames have been consumed; give the output a moment to play them out.
                handler.removeCallbacks(playing.end)
                handler.postDelayed(playing.end, OUTPUT_TAIL_MS)
            }

            override fun onPeriodicNotification(t: AudioTrack?) = Unit
        }, handler)
        track.setNotificationMarkerPosition(playing.frames)
        focus.request()
        try {
            track.play()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot start playback", e)
            finish(playing.id)
            return
        }
        // Safety net in case the marker never fires.
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

    /** Creates a static AudioTrack holding [pcm]. Called on the worker thread. */
    private fun buildTrack(pcm: ShortArray): Prepared? = try {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (track.write(pcm, 0, pcm.size) == pcm.size) {
            Prepared(track, pcm.size)
        } else {
            Log.e(TAG, "Cannot write PCM to the AudioTrack")
            track.release()
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot create AudioTrack", e)
        null
    }

    private fun releaseTrack(track: AudioTrack) {
        try {
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot release AudioTrack", e)
        }
    }

    private fun daemonExecutor(name: String): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }

    private companion object {
        const val TAG = "ClipAnnouncer"
        const val FOCUS_TAIL_MS = 300L
        const val OUTPUT_TAIL_MS = 60L
        const val FALLBACK_MS = 500L

        const val SAMPLE_RATE = ClipLibrary.SAMPLE_RATE
    }
}
