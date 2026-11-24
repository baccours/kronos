package com.baccours.kronos.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/** The part of [PowerManager.WakeLock] used here, so the renewal timing can be tested without a device. */
internal interface RawWakeLock {
    val isHeld: Boolean

    fun acquire(timeoutMs: Long)

    fun release()
}

/**
 * Partial wake lock with adaptive renewal window to prevent battery drain on crashes.
 */
internal class WakeLockHolder(
    private val lock: RawWakeLock,
    /** Monotonic clock (milliseconds). */
    private val clock: () -> Long
) : HeldLock {

    constructor(context: Context, tag: String) : this(PowerManagerLock(context, tag), SystemClock::elapsedRealtime)

    private var acquiredAt = 0L

    override fun acquire() {
        try {
            lock.acquire(ACTIVE_TIMEOUT_MS)
            acquiredAt = clock()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    override fun renewIfDue() {
        if (lock.isHeld && clock() - acquiredAt > RENEW_THRESHOLD_MS) {
            acquire()
        }
    }

    override fun release() {
        try {
            if (lock.isHeld) lock.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    internal companion object {
        const val TAG = "WakeLockHolder"
        // 15-minute safety timeout
        const val ACTIVE_TIMEOUT_MS = 15L * 60 * 1000
        // Renew every 10 minutes while workout runs
        const val RENEW_THRESHOLD_MS = 10L * 60 * 1000
    }
}

private class PowerManagerLock(context: Context, tag: String) : RawWakeLock {

    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        .apply { setReferenceCounted(false) }

    override val isHeld: Boolean get() = lock.isHeld

    override fun acquire(timeoutMs: Long) = lock.acquire(timeoutMs)

    override fun release() = lock.release()
}
