package com.baccours.kronos.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Partial wake lock with adaptive renewal window to prevent battery drain on crashes.
 */
class WakeLockHolder(context: Context, tag: String) {

    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        .apply { setReferenceCounted(false) }

    private var acquiredAt = 0L

    fun acquire() {
        try {
            lock.acquire(ACTIVE_TIMEOUT_MS)
            acquiredAt = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    fun renewIfDue() {
        if (lock.isHeld && SystemClock.elapsedRealtime() - acquiredAt > RENEW_THRESHOLD_MS) {
            acquire()
        }
    }

    fun release() {
        try {
            if (lock.isHeld) lock.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private companion object {
        const val TAG = "WakeLockHolder"
        // 15-minute safety timeout
        const val ACTIVE_TIMEOUT_MS = 15L * 60 * 1000
        // Renew every 10 minutes while workout runs
        const val RENEW_THRESHOLD_MS = 10L * 60 * 1000
    }
}