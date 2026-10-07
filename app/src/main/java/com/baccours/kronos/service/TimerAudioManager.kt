package com.baccours.kronos.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.baccours.kronos.engine.Cue
import java.util.Locale

/**
 * Plays beeps and spoken cues on the media stream (so they are not muted by the
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

    private val toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: RuntimeException) {
        Log.e(TAG, "ToneGenerator unavailable", e)
        null
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
            Cue.Beep -> playBeep()
            Cue.StartBuzzer -> playStartBuzzer()
            Cue.Celebration -> playCelebration()
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

    fun playBeep() = tone(ToneGenerator.TONE_PROP_BEEP, 300)

    fun playStartBuzzer() = tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)

    /** Three prompts; each one is scheduled so it doesn't cut the previous one off. */
    fun playCelebration() {
        tone(ToneGenerator.TONE_PROP_PROMPT, 200)
        handler.postDelayed({ tone(ToneGenerator.TONE_PROP_PROMPT, 200) }, 300)
        handler.postDelayed({ tone(ToneGenerator.TONE_PROP_PROMPT, 400) }, 600)
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
        toneGenerator?.release()
    }

    // ------------------------------------------------------------------ internals

    private fun tone(type: Int, durationMs: Int) {
        val generator = toneGenerator ?: return
        requestFocus()
        generator.startTone(type, durationMs)
        scheduleAbandon(durationMs + FOCUS_TAIL_MS)
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
        const val FOCUS_TAIL_MS = 700L
    }
}
