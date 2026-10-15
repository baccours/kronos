package com.baccours.kronos.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

private const val TAG = "StaticTrack"

/**
 * Creates a static (fully preloaded) mono 16-bit [AudioTrack] at [PcmUtil.SAMPLE_RATE] holding [pcm],
 * or returns null, after logging, when that fails. Safe to call from any thread.
 */
fun createStaticTrack(pcm: ShortArray, attributes: AudioAttributes): AudioTrack? {
    if (pcm.isEmpty()) return null
    return try {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(PcmUtil.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (track.write(pcm, 0, pcm.size) == pcm.size) {
            track
        } else {
            Log.e(TAG, "Cannot write PCM to the AudioTrack")
            track.release()
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot create AudioTrack", e)
        null
    }
}

/**
 * A short sound (beep, buzzer, ...) that can be replayed at will. Its PCM is generated and its
 * track created on first use, or earlier through [prepare]. Main thread only.
 */
class StaticTrack(
    private val pcmSource: () -> ShortArray,
    private val attributes: AudioAttributes,
    private val focus: AudioFocusController
) {
    private var track: AudioTrack? = null
    private var durationMs = 0L

    /** Creates the track now, so that the first [play] does not have to. */
    fun prepare() {
        ensureTrack()
    }

    /** Plays the sound from the start, restarting it if it is still playing. */
    fun play() {
        val t = ensureTrack() ?: return
        focus.request()
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot play static sound", e)
        }
        focus.releaseAfter(durationMs + AudioFocusController.TAIL_MS)
    }

    fun release() {
        val t = track ?: return
        track = null
        try {
            t.release()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot release AudioTrack", e)
        }
    }

    private fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        val pcm = pcmSource()
        val created = createStaticTrack(pcm, attributes) ?: return null
        durationMs = pcm.size * 1000L / PcmUtil.SAMPLE_RATE
        track = created
        return created
    }
}
