package com.baccours.kronos.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler

/**
 * Briefly ducks other audio (music) while a cue plays. Shared by the synthesized sounds and the
 * announcer so that one of them finishing never drops the focus while the other is still playing.
 * Must be used from the main thread.
 */
class AudioFocusController(
    context: Context,
    private val handler: Handler,
    private val attributes: AudioAttributes
) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** While this returns true the focus is kept, even when a scheduled release comes due. */
    var isBusy: () -> Boolean = { false }

    private var hasFocus = false
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }
    private val abandonRunnable = Runnable { if (!isBusy()) abandon() }

    fun request() {
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

    /** Gives the focus back after [delayMs], unless something is still playing by then. */
    fun releaseAfter(delayMs: Long) {
        handler.removeCallbacks(abandonRunnable)
        handler.postDelayed(abandonRunnable, delayMs)
    }

    /** Gives the focus back right now. */
    fun release() {
        handler.removeCallbacks(abandonRunnable)
        abandon()
    }

    private fun abandon() {
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
}
