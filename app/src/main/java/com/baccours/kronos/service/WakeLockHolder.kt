package com.baccours.kronos.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock

/** Partial wake lock with a safety timeout, so a bug can never keep the CPU awake forever. */
class WakeLockHolder(context: Context, tag: String) {

    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        .apply { setReferenceCounted(false) }
    private var acquiredAt = 0L

    fun acquire() {
        lock.acquire(TIMEOUT_MS)
        acquiredAt = SystemClock.elapsedRealtime()
    }

    /** Extends the timeout hourly while running, so a long workout never outlives it. */
    fun renewIfDue() {
        if (SystemClock.elapsedRealtime() - acquiredAt > RENEW_MS) acquire()
    }

    fun release() {
        if (lock.isHeld) lock.release()
    }

    private companion object {
        const val TIMEOUT_MS = 6L * 60 * 60 * 1000
        const val RENEW_MS = 60L * 60 * 1000
    }
}
