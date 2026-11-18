package com.baccours.kronos.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Android implementations of the audio seams in `AudioPorts.kt`, and the factories that wire them.
 * Kept thin on purpose: this is the only audio code that unit tests cannot exercise.
 */

internal class HandlerMainThread(private val handler: Handler) : MainThread {
    override fun post(task: Runnable) {
        handler.post(task)
    }

    override fun postDelayed(task: Runnable, delayMs: Long) {
        handler.postDelayed(task, delayMs)
    }

    override fun cancel(task: Runnable) = handler.removeCallbacks(task)
}

/** A static [AudioTrack] holding one announcement. */
internal class AudioTrackClip(
    private val track: AudioTrack,
    override val frames: Int,
    private val handler: Handler
) : PreparedClip {

    override fun start(onLastFrame: () -> Unit) {
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) = onLastFrame()
            override fun onPeriodicNotification(t: AudioTrack?) = Unit
        }, handler)
        track.notificationMarkerPosition = frames
        track.play()
    }

    override fun release() = track.release()
}

/** Focus requests through [AudioManager], with the API 26 split. */
internal class AudioManagerFocus(
    context: Context,
    private val handler: Handler,
    private val attributes: AudioAttributes
) : FocusSystem {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var onLoss: () -> Unit = {}

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            onLoss()
        }
    }

    override fun request(onLoss: () -> Unit): Boolean {
        this.onLoss = onLoss
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
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }
}

/** The production [ClipAnnouncer]: main-thread [Handler], a daemon worker thread, static [AudioTrack]s. */
fun ClipAnnouncer(
    context: Context,
    focus: AudioFocusController,
    library: ClipLibrary = ClipLibrary.get(context)
): ClipAnnouncer {
    val handler = Handler(Looper.getMainLooper())
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    return ClipAnnouncer(
        focus = focus,
        library = library,
        mainThread = HandlerMainThread(handler),
        worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "clip-announcer").apply { isDaemon = true }
        },
        clips = { pcm ->
            createStaticTrack(pcm, attributes)?.let { AudioTrackClip(it, pcm.size, handler) }
        }
    )
}

/** The production [AudioFocusController], backed by [AudioManager]. */
fun AudioFocusController(
    context: Context,
    handler: Handler,
    attributes: AudioAttributes
): AudioFocusController =
    AudioFocusController(HandlerMainThread(handler), AudioManagerFocus(context, handler, attributes))
