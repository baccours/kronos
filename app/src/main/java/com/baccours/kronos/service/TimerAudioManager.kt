package com.baccours.kronos.service

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import com.baccours.kronos.audio.Announcer
import com.baccours.kronos.audio.AudioFocusController
import com.baccours.kronos.audio.ClipAnnouncer
import com.baccours.kronos.audio.StaticTrack
import com.baccours.kronos.audio.ToneSynth
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.CuePlayer
import com.baccours.kronos.engine.Speech

/**
 * Plays the workout's cues on the media stream (so they are not muted by the notification
 * volume) and briefly ducks other audio (music) while a cue plays.
 * Beeps and buzzers are synthesized ([ToneSynth]) and played as [StaticTrack]s; spoken cues are
 * delegated to an [Announcer] (recorded clips by default), which is the place to plug in another
 * voice, e.g. text-to-speech.
 * Must be used from the main thread.
 */
class TimerAudioManager(
    context: Context,
    announcerFactory: (AudioFocusController) -> Announcer = {
        ClipAnnouncer(context.applicationContext, it)
    }
) : CuePlayer {

    private val handler = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val focus = AudioFocusController(context.applicationContext, handler, attributes)
    private val announcer: Announcer = announcerFactory(focus)

    private val beep = StaticTrack(ToneSynth::beep, attributes, focus)
    private val startBuzzer = StaticTrack(ToneSynth::startBuzzer, attributes, focus)
    private val celebration = StaticTrack(ToneSynth::celebration, attributes, focus)
    private val tones = listOf(beep, startBuzzer, celebration)

    init {
        // Keep the focus while an announcement is still playing.
        focus.isBusy = { announcer.isSpeaking }
    }

    override fun play(cue: Cue) {
        when (cue) {
            is Cue.Speak -> announcer.say(cue)
            Cue.Beep -> beep.play()
            Cue.StartBuzzer -> startBuzzer.play()
            Cue.Celebration -> celebration.play()
        }
    }

    /**
     * Gets what is needed ready before it is: lets the announcer prepare [speeches] (e.g. decode
     * clips) and creates the tone tracks, so that the first beep is not delayed.
     */
    override fun prepare(speeches: List<Speech>) {
        announcer.prepare(speeches)
        // Posted, so that the cues of the starting workout are not delayed by it.
        handler.post { tones.forEach { it.prepare() } }
    }

    override fun stopSpeaking() = announcer.stop()

    override fun release() {
        handler.removeCallbacksAndMessages(null)
        announcer.release()
        focus.release()
        tones.forEach { it.release() }
    }
}
