package com.baccours.kronos.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TimerAudioManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            } else {
                Log.e("TimerAudioManager", "TTS Initialization failed")
            }
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun playBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    fun playLongBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }

    fun playStartBuzzer() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
    }

    fun playCelebration() {
        // Simple celebration sequence
        toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        toneGenerator.release()
    }
}
