package com.baccours.kronos.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.baccours.kronos.engine.Cue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plays beeps, buzzers, and spoken cues on the media stream (so they are not muted by the
 * notification volume) and briefly ducks other audio (music) while a cue plays.
 * Must be used from the main thread.
 */
class TimerAudioManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    // --- Text to speech
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val pending = ArrayList<Cue.Speak>()
    private var queuedUtterances = 0
    private var utteranceCounter = 0

    // --- Audio focus
    private var hasFocus = false
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }
    private val abandonRunnable = Runnable {
        if (queuedUtterances == 0) abandonFocus()
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = utteranceFinished()

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = utteranceFinished()

        override fun onStop(utteranceId: String?, interrupted: Boolean) = utteranceFinished()
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            handler.post {
                val engine = tts ?: return@post
                if (status == TextToSpeech.SUCCESS) {
                    // Spoken cues are always English (US), whatever the device language is.
                    val result = engine.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.w(TAG, "English (US) voice not available on this device")
                    }
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    engine.setOnUtteranceProgressListener(progressListener)
                    isTtsReady = true
                    val queued = pending.toList()
                    pending.clear()
                    queued.forEach { speak(it.text, it.interrupt) }
                } else {
                    Log.e(TAG, "TTS initialization failed")
                    pending.clear()
                }
            }
        }
    }

    private fun utteranceFinished() {
        handler.post {
            queuedUtterances = (queuedUtterances - 1).coerceAtLeast(0)
            if (queuedUtterances == 0) scheduleAbandon(FOCUS_TAIL_MS)
        }
    }

    // ------------------------------------------------------------------ public API

    fun play(cue: Cue) {
        when (cue) {
            is Cue.Speak -> speak(cue.text, cue.interrupt)
            Cue.Beep -> beep.play()
            Cue.StartBuzzer -> startBuzzer.play()
            Cue.Celebration -> celebration.play()
        }
    }

    fun speak(text: String, interrupt: Boolean = false) {
        val engine = tts
        if (engine == null || !isTtsReady) {
            // TTS initializes asynchronously: keep early cues instead of dropping them.
            if (interrupt) pending.clear()
            pending.add(Cue.Speak(text, interrupt))
            return
        }
        requestFocus()
        if (interrupt) queuedUtterances = 0
        queuedUtterances++
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(text, mode, null, "kronos-${utteranceCounter++}")
        if (result != TextToSpeech.SUCCESS) {
            queuedUtterances = (queuedUtterances - 1).coerceAtLeast(0)
        }
    }

    fun stopSpeaking() {
        pending.clear()
        if (isTtsReady) tts?.stop()
        queuedUtterances = 0
        scheduleAbandon(FOCUS_TAIL_MS)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        abandonFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
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
            requestFocus()
            try {
                t.stop()
                t.reloadStaticData()
                t.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play static sound", e)
            }
            scheduleAbandon(durationMs + FOCUS_TAIL_MS)
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

    private fun requestFocus() {
        handler.removeCallbacks(abandonRunnable)
        if (hasFocus) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun scheduleAbandon(delayMs: Long) {
        handler.removeCallbacks(abandonRunnable)
        handler.postDelayed(abandonRunnable, delayMs)
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        hasFocus = false
    }

    private companion object {
        const val TAG = "TimerAudioManager"
        const val FOCUS_TAIL_MS = 300L
        const val SAMPLE_RATE = 44100
    }
}
