package com.baccours.kronos.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.CuePlayer
import com.baccours.kronos.engine.Speech
import com.baccours.kronos.model.AppSettings

/**
 * Audio manager playing synthesized tones and spoken cues with audio ducking on the media stream.
 * Main-thread only.
 */
class TimerAudioManager(
    context: Context,
    /** Read at play time, so settings changes apply immediately. */
    private val settings: () -> AppSettings,
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
        // Maintain focus during speech playback
        focus.isBusy = { announcer.isSpeaking }
        // Cut speech when another app takes focus.
        focus.onLost = { announcer.stop() }
    }

    override fun play(cue: Cue) {
        if (!settings().allows(cue)) return
        when (cue) {
            is Cue.Speak -> announcer.say(cue)
            Cue.Beep -> beep.play()
            Cue.StartBuzzer -> startBuzzer.play()
            Cue.Celebration -> celebration.play()
        }
    }

    /** Preloads speech assets and tone tracks for immediate playback. */
    override fun prepare(speeches: List<Speech>) {
        // Clips for a muted voice are decoded on demand if it is switched back on.
        if (settings().voice) announcer.prepare(speeches)
        // Post tone initialization to prevent delaying initial start cues
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
