package com.baccours.kronos.service

import android.os.Handler
import com.baccours.kronos.engine.TickScheduler

/**
 * Handler-backed implementation of [TickScheduler]. Delays count uptime, so this relies on the
 * service's wake lock; a late tick is harmless, as the engine recomputes from the real clock.
 */
class HandlerTickScheduler(private val handler: Handler) : TickScheduler {

    private var pending: Runnable? = null

    override fun scheduleAfter(delayMs: Long, task: () -> Unit) {
        cancel()
        val runnable = Runnable {
            pending = null
            task()
        }
        pending = runnable
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
