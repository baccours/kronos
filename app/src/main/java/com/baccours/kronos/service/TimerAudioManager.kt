package com.baccours.kronos.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.baccours.kronos.audio.Announcer
import com.baccours.kronos.audio.AudioFocusController
import com.baccours.kronos.audio.ClipAnnouncer
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plays beeps, buzzers, and spoken cues on the media stream (so they are not muted by the
 * notification volume) and briefly ducks other audio (music) while a cue plays.
 * Beeps are synthesized here; spoken cues are delegated to an [Announcer] (recorded clips by
 * default), which is the place to plug in another voice, e.g. text-to-speech.
 * Must be used from the main thread.
 */
class TimerAudioManager(
    context: Context,
    announcerFactory: (AudioFocusController) -> Announcer = {
        ClipAnnouncer(context.applicationContext, it)
    }
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // --- Static synthesized sounds
    private val beep by lazy {
        StaticSound(generatePcm(250, wave = square(880.0)))
    }

    private val startBuzzer by lazy {
        // Square wave = harsh "buzzer" timbre. Keep amplitude lower to avoid clipping.
        StaticSound(generatePcm(550, wave = square(440.0)))
    }

    private val celebration by lazy {
        // C Major ascending arpeggio: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz)
        StaticSound(
            note(523.25, 150) + note(659.25, 150) + note(783.99, 150) + note(1046.5, 400)
        )
    }

    // --- Audio focus and spoken cues
    private val focus = AudioFocusController(appContext, handler, attributes)
    private val announcer: Announcer = announcerFactory(focus)

    init {
        // Keep the focus while an announcement is still playing.
        focus.isBusy = { announcer.isSpeaking }
    }

    // ------------------------------------------------------------------ public API

    fun play(cue: Cue) {
        when (cue) {
            is Cue.Speak -> announcer.say(cue)
            Cue.Beep -> beep.play()
            Cue.StartBuzzer -> startBuzzer.play()
            Cue.Celebration -> celebration.play()
        }
    }

    /** Lets the announcer get [speeches] ready (e.g. decode clips) before they are needed. */
    fun prepare(speeches: List<Speech>) = announcer.prepare(speeches)

    fun stopSpeaking() = announcer.stop()

    fun release() {
        handler.removeCallbacksAndMessages(null)
        announcer.release()
        focus.release()
        if (beep.isInitialized()) beep.release()
        if (startBuzzer.isInitialized()) startBuzzer.release()
        if (celebration.isInitialized()) celebration.release()
    }

    // ------------------------------------------------------------------ internals

    private inner class StaticSound(private val pcm: ByteArray) {
        private var track: AudioTrack? = null
        val durationMs: Int = (pcm.size / 2) * 1000 / SAMPLE_RATE

        fun play() {
            val t = ensureTrack() ?: return
            focus.request()
            try {
                t.stop()
                t.reloadStaticData()
                t.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play static sound", e)
            }
            focus.releaseAfter(durationMs + FOCUS_TAIL_MS)
        }

        fun isInitialized(): Boolean = track != null

        private fun ensureTrack(): AudioTrack? {
            track?.let { return it }
            return try {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                    .also {
                        it.write(pcm, 0, pcm.size)
                        track = it
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AudioTrack", e)
                null
            }
        }

        fun release() {
            track?.let {
                try {
                    if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                    it.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release AudioTrack", e)
                }
            }
            track = null
        }
    }

    private fun square(freqHz: Double): (Double) -> Double =
        { t -> if (sin(2 * PI * freqHz * t) >= 0) 1.0 else -1.0 }

    private fun note(freqHz: Double, ms: Int) =
        generatePcm(ms, wave = square(freqHz))

    private fun generatePcm(
        durationMs: Int,
        targetRms: Double = 0.30,
        fadeMs: Int = 10,
        wave: (timeSec: Double) -> Double
    ): ByteArray {
        val total = SAMPLE_RATE * durationMs / 1000
        val fade = (SAMPLE_RATE * fadeMs / 1000).coerceAtMost(total / 2)

        val samples = DoubleArray(total) { i ->
            var s = wave(i.toDouble() / SAMPLE_RATE)
            if (i < fade) s *= i.toDouble() / fade
            else if (i >= total - fade) s *= (total - i).toDouble() / fade
            s
        }

        val rms = sqrt(samples.sumOf { it * it } / total)
        val gain = if (rms > 0.0) targetRms / rms else 0.0

        val pcm = ByteArray(total * 2)
        for (i in 0 until total) {
            val v = (samples[i] * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private companion object {
        const val TAG = "TimerAudioManager"
        const val FOCUS_TAIL_MS = 300L
        const val SAMPLE_RATE = 44100
    }
}
