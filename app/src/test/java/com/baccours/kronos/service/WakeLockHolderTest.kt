package com.baccours.kronos.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A wake lock that the "system" drops by itself when its timeout ends, like the real one. */
internal class ExpiringRawLock(private val now: () -> Long) : RawWakeLock {
    private var heldUntil = Long.MIN_VALUE
    private var explicitlyReleased = true

    var acquires = 0
        private set
    var releases = 0
        private set
    var lastTimeoutMs = 0L
        private set
    var failOnAcquire: RuntimeException? = null
    var failOnRelease: RuntimeException? = null

    override val isHeld: Boolean get() = !explicitlyReleased && now() < heldUntil

    override fun acquire(timeoutMs: Long) {
        failOnAcquire?.let { throw it }
        acquires++
        lastTimeoutMs = timeoutMs
        heldUntil = now() + timeoutMs
        explicitlyReleased = false
    }

    override fun release() {
        failOnRelease?.let { throw it }
        releases++
        explicitlyReleased = true
    }
}

class WakeLockHolderTest {

    private var now = 0L
    private val raw = ExpiringRawLock { now }
    private val holder = WakeLockHolder(raw) { now }

    private val minute = 60_000L

    @Test
    fun theLockIsTakenWithAFifteenMinuteSafetyTimeout() {
        holder.acquire()
        assertTrue(raw.isHeld)
        assertEquals(15 * minute, raw.lastTimeoutMs)
    }

    @Test
    fun withoutRenewalTheSystemDropsTheLockAfterTheTimeout() {
        holder.acquire()
        now = 15 * minute
        assertFalse("the fake must model the platform, or the tests below prove nothing", raw.isHeld)
    }

    @Test
    fun theLockIsNotRenewedBeforeTenMinutes() {
        holder.acquire()
        now = 10 * minute // exactly at the threshold: not yet
        holder.renewIfDue()
        assertEquals(1, raw.acquires)
    }

    @Test
    fun theLockIsRenewedOnceTenMinutesHavePassed() {
        holder.acquire()
        now = 10 * minute + 1
        holder.renewIfDue()
        assertEquals(2, raw.acquires)
        assertEquals(15 * minute, raw.lastTimeoutMs)
    }

    @Test
    fun aRenewalRestartsTheTenMinuteWindow() {
        holder.acquire()
        now = 10 * minute + 1
        holder.renewIfDue()
        now = 20 * minute // only 9 min 59 s since the renewal
        holder.renewIfDue()
        assertEquals(2, raw.acquires)
        now = 20 * minute + 2
        holder.renewIfDue()
        assertEquals(3, raw.acquires)
    }

    @Test
    fun aReleasedLockIsNeverTakenBackByAStrayRenewal() {
        holder.acquire()
        holder.release()
        now = 60 * minute
        holder.renewIfDue() // a tick racing the pause
        assertFalse(raw.isHeld)
        assertEquals(1, raw.acquires)
    }

    @Test
    fun aLockTheSystemAlreadyDroppedIsNotSilentlyRetaken() {
        holder.acquire()
        now = 16 * minute // timed out: nobody renewed in time
        holder.renewIfDue()
        assertFalse(raw.isHeld)
        assertEquals(1, raw.acquires)
    }

    @Test
    fun releasingALockThatIsNotHeldDoesNothing() {
        holder.release()
        assertEquals(0, raw.releases)
    }

    @Test
    fun checkedEverySecondTheLockSurvivesTwoHours() {
        holder.acquire()
        var second = 0L
        while (second <= 2 * 3_600L) {
            now = second * 1_000
            holder.renewIfDue()
            assertTrue("lost at ${second}s", raw.isHeld)
            second++
        }
        // One renewal about every ten minutes, not one per tick.
        assertTrue("acquires: ${raw.acquires}", raw.acquires in 12..14)
    }

    @Test
    fun aMissingPermissionOrASystemErrorOnAcquireIsContained() {
        raw.failOnAcquire = SecurityException("no WAKE_LOCK")
        holder.acquire() // must not throw: the workout can still run, just without the lock
        assertFalse(raw.isHeld)
    }

    @Test
    fun aFailedAcquireDoesNotStartTheRenewalWindow() {
        raw.failOnAcquire = SecurityException("no WAKE_LOCK")
        holder.acquire()
        raw.failOnAcquire = null
        now = 5 * minute
        holder.renewIfDue() // not held, so nothing happens
        assertEquals(0, raw.acquires)
    }

    @Test
    fun aFailureOnReleaseIsContained() {
        holder.acquire()
        raw.failOnRelease = IllegalStateException("under-locked")
        holder.release() // must not throw into the service teardown
    }
}
