package com.baccours.kronos.audio

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.model.AppSettings

/** Whether the user's settings allow [cue] to be played. */
fun AppSettings.allows(cue: Cue): Boolean = when (cue) {
    is Cue.Speak -> voice
    Cue.Beep, Cue.StartBuzzer, Cue.Celebration -> beeps
}
