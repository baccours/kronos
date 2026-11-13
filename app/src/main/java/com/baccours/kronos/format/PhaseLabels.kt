package com.baccours.kronos.format

import android.content.Context
import com.baccours.kronos.R
import com.baccours.kronos.model.Phase

/** Phase display label, in capitals. */
fun Context.phaseLabel(phase: Phase, workIndex: Int): String = when (phase) {
    Phase.READY -> getString(R.string.phase_ready)
    Phase.WORK ->
        if (workIndex > 0) getString(R.string.phase_work_n, workIndex)
        else getString(R.string.phase_work)
    Phase.REST -> getString(R.string.phase_rest)
    Phase.SET_REST -> getString(R.string.phase_set_rest)
}.caps()
