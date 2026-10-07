package com.baccours.kronos.ui

import android.content.Context
import com.baccours.kronos.R
import com.baccours.kronos.model.Phase

/** "WORK 2", "REST", "GET READY", ... shared by the running screen and the notification. */
fun Context.phaseLabel(phase: Phase, workIndex: Int): String = when (phase) {
    Phase.READY -> getString(R.string.phase_ready)
    Phase.WORK ->
        if (workIndex > 0) getString(R.string.phase_work_n, workIndex)
        else getString(R.string.phase_work)
    Phase.REST -> getString(R.string.phase_rest)
    Phase.SET_REST -> getString(R.string.phase_set_rest)
}
