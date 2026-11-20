package com.baccours.kronos.service

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The service's workout choreography (foreground, wake lock, pause timeout, end of workout) run
 * against fakes and a virtual clock. These are the situations nobody can reproduce by hand on a
 * device: a refused foreground start, an hour-long pause, a start racing the end of a workout.
 */
class WorkoutSessionTest {

    private val rigs = ArrayList<Rig>()

    @After
    fun closeRigs() = rigs.forEach { it.close() }

    private fun rig(lockFactory: (Journal, VirtualTime) -> HeldLock = { j, _ -> FakeLock(j) }) =
        Rig(lockFactory).also { rigs += it }

    private fun Rig.fakeLock() = lock as FakeLock

    // ---- starting ----

    @Test
    fun aStartPromotesTheServiceBeforeAnythingElseHappens() {
        val r = rig()
        r.session.start(shortWorkout)
        r.journal.assertInOrder(
            "host.enterForeground(RUNNING)",
            "lock.acquire",
            "notifier.update(RUNNING)"
        )
        assertEquals(TimerStatus.RUNNING, r.status)
        assertEquals(Phase.READY, r.state.phase)
        assertEquals(shortWorkout, r.state.config)
        assertTrue(r.ticker.isPending)
        assertTrue(r.fakeLock().held)
        assertEquals(0, r.host.stopSelfCalls)
    }

    @Test
    fun anUndecodableStartStillHonoursTheForegroundPromiseThenFailsCleanly() {
        val r = rig()
        r.session.start(null)
        // startForegroundService() obliges the service to call startForeground() quickly.
        assertEquals(1, r.journal.count("host.enterForeground(NOT_STARTED)"))
        assertEquals(1, r.startFailures.size)
        assertEquals(1, r.host.stopSelfCalls)
        assertEquals(0, r.fakeLock().acquires)
        assertFalse(r.ticker.isPending)
        assertEquals(TimerStatus.NOT_STARTED, r.status)
    }

    @Test
    fun aRefusedForegroundStartLeavesNoWorkoutAndNoWakeLock() {
        val r = rig()
        r.host.allowForeground = false
        r.session.start(shortWorkout)
        assertEquals(1, r.startFailures.size)
        assertEquals("the start itself must end the service", 1, r.host.stopSelfCalls)
        assertEquals(0, r.fakeLock().acquires)
        assertFalse(r.ticker.isPending)
        assertEquals(TimerStatus.NOT_STARTED, r.status)

        // The next attempt, once allowed, works as if nothing had happened.
        r.host.allowForeground = true
        r.session.start(shortWorkout)
        assertEquals(TimerStatus.RUNNING, r.status)
        assertEquals(1, r.startFailures.size)
    }

    @Test
    fun aDuplicateStartKeepsTheRunningWorkoutUntouched() {
        val r = rig()
        r.session.start(shortWorkout)
        r.time.advanceTo(12_000)
        val before = r.state

        r.session.start(TimerConfig(sets = 5, tasks = listOf(work(60))))

        assertEquals(shortWorkout, r.state.config)
        assertEquals(before.segmentIndex, r.state.segmentIndex)
        assertEquals(2, r.journal.count("host.enterForeground(RUNNING)")) // re-promoted, as the OS demands
        assertEquals(1, r.fakeLock().acquires)
        assertEquals(0, r.startFailures.size)
    }

    @Test
    fun aDuplicateStartWhilePausedKeepsItPaused() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        r.session.start(shortWorkout)
        assertEquals(TimerStatus.PAUSED, r.status)
        assertTrue(r.alarm.armed)
        assertFalse(r.fakeLock().held)
    }

    @Test
    fun aStartDuringTheClosingLingerCancelsTheEndOfTheService() {
        val r = rig()
        r.session.start(shortWorkout)
        r.runToTheEnd()
        assertTrue(r.linger.isPending)

        r.session.start(stopwatch) // "Again" tapped while the closing audio plays
        assertFalse(r.linger.isPending)
        assertEquals(TimerStatus.RUNNING, r.status)

        r.time.advanceBy(30_000) // far past the linger
        assertEquals("the service must not stop under a running workout", 0, r.host.stopSelfCalls)
        assertEquals(0, r.journal.count("host.leaveForeground(remove=false)"))
    }

    // ---- commands ----

    @Test
    fun commandsForAWorkoutThatDoesNotExistAreHarmless() {
        val r = rig()
        r.session.pause()
        r.session.resume()
        r.session.skip()
        r.session.lap()
        r.session.prepareCues()
        assertTrue(r.journal.isEmpty())
        assertEquals(TimerStatus.NOT_STARTED, r.status)

        r.session.endOfCommand()
        assertEquals("no idle service left behind", 1, r.host.stopSelfCalls)
    }

    @Test
    fun aCommandForARunningWorkoutDoesNotStopTheService() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.lap()
        r.session.endOfCommand()
        assertEquals(0, r.host.stopSelfCalls)
    }

    @Test
    fun skipAndLapReachTheEngine() {
        val r = rig()
        r.session.start(stopwatch)
        r.session.skip() // get-ready -> work
        assertEquals(Phase.WORK, r.state.phase)
        r.time.advanceBy(5_000)
        r.session.lap()
        assertEquals(1, r.state.laps.size)
    }

    @Test
    fun resumingAWorkoutThatIsNotPausedChangesNothing() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.resume()
        assertEquals(1, r.fakeLock().acquires)
        assertEquals(0, r.journal.count("alarm.cancel"))
        assertEquals(TimerStatus.RUNNING, r.status)
    }

    @Test
    fun prepareCuesReloadsTheRunningWorkoutOnly() {
        val r = rig()
        r.session.prepareCues()
        assertTrue(r.cues.prepared.isEmpty())
        r.session.start(shortWorkout)
        assertEquals(1, r.cues.prepared.size) // at the start
        r.session.prepareCues()
        assertEquals(2, r.cues.prepared.size)
    }

    // ---- pause and the forgotten-pause timeout ----

    @Test
    fun pausingFreesTheCpuAndArmsTheTimeout() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        assertEquals(TimerStatus.PAUSED, r.status)
        assertFalse("the CPU must be free while paused", r.fakeLock().held)
        assertTrue(r.alarm.armed)
        assertFalse(r.ticker.isPending)
    }

    @Test
    fun resumingDisarmsTheTimeoutBeforeTakingTheCpuBack() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        r.session.resume()
        r.journal.assertInOrder("alarm.start", "alarm.cancel", "lock.acquire")
        assertEquals(TimerStatus.RUNNING, r.status)
        assertTrue(r.fakeLock().held)
        assertFalse(r.alarm.armed)
        assertTrue(r.ticker.isPending)

        r.alarm.fire() // the hour is over, but nobody is paused anymore
        assertEquals(TimerStatus.RUNNING, r.status)
    }

    @Test
    fun aForgottenPauseEndsTheWorkoutAndTellsTheUser() {
        val r = rig()
        r.session.start(shortWorkout)
        r.time.advanceTo(12_000)
        r.session.pause()

        r.alarm.fire()

        assertEquals(TimerStatus.NOT_STARTED, r.status)
        assertFalse(r.fakeLock().held)
        assertFalse(r.ticker.isPending)
        r.journal.assertInOrder(
            "host.leaveForeground(remove=true)",
            "notifier.cancel",
            "host.stopSelf",
            "notifier.pauseTimeout" // after the cancel, or it would be canceled with it
        )
        assertEquals(1, r.journal.count("notifier.pauseTimeout"))
    }

    @Test
    fun aPauseTimeoutThatFiresAfterStopDoesNothing() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        r.session.stop()
        assertFalse(r.alarm.armed)
        r.alarm.fire()
        assertEquals(0, r.journal.count("notifier.pauseTimeout"))
    }

    @Test
    fun pausingAFinishedWorkoutArmsNothing() {
        val r = rig()
        r.session.start(shortWorkout)
        r.runToTheEnd()
        r.session.pause()
        assertEquals(0, r.alarm.starts)
        assertEquals(TimerStatus.FINISHED, r.status)
    }

    @Test
    fun finishingWhilePausedDisarmsTheTimeout() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        assertTrue(r.alarm.armed)

        r.skipToTheEnd()

        assertEquals(TimerStatus.FINISHED, r.status)
        assertFalse(r.alarm.armed)
        assertEquals(1, r.journal.count("notifier.show(FINISHED)"))
    }

    // ---- stopping ----

    @Test
    fun stopTearsEverythingDownInTheRightOrder() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        r.journal.clear()

        r.session.stop()

        r.journal.assertInOrder(
            "alarm.cancel",
            "lock.release",
            "host.leaveForeground(remove=true)",
            "notifier.cancel",
            "host.stopSelf"
        )
        assertEquals(TimerStatus.NOT_STARTED, r.status)
        assertFalse(r.ticker.isPending)
    }

    @Test
    fun aTickQueuedBeforeStopCannotRepaintTheScreen() {
        val r = rig()
        r.session.start(shortWorkout)
        r.time.advanceTo(3_000)
        r.session.stop()
        r.time.advanceBy(60_000)
        assertEquals(TimerStatus.NOT_STARTED, r.status)
        assertNull(r.state.config)
    }

    @Test
    fun stopWithoutAWorkoutStillCleansUp() {
        val r = rig()
        r.session.stop()
        r.journal.assertInOrder("host.leaveForeground(remove=true)", "notifier.cancel", "host.stopSelf")
        assertEquals(TimerStatus.NOT_STARTED, r.status)
    }

    @Test
    fun stopCanBeRepeatedSafely() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.stop()
        r.session.stop()
        assertEquals(TimerStatus.NOT_STARTED, r.status)
        assertFalse(r.fakeLock().held)
    }

    // ---- the end of a workout ----

    @Test
    fun aFinishedWorkoutStaysForegroundUntilTheClosingAudioHasPlayed() {
        val r = rig()
        r.session.start(shortWorkout)
        r.runToTheEnd()

        assertEquals(TimerStatus.FINISHED, r.status)
        assertEquals(1, r.journal.count("notifier.show(FINISHED)"))
        assertEquals("a finished state is not a notification update", 0, r.journal.count("notifier.update(FINISHED)"))
        assertTrue("CPU held for the closing audio", r.fakeLock().held)
        assertEquals(0, r.host.stopSelfCalls)
        assertEquals(WorkoutSession.FINISH_LINGER_MS, r.linger.dueAt!! - r.linger.scheduledAt)

        r.time.advanceTo(r.linger.dueAt!! - 1)
        assertEquals(0, r.host.stopSelfCalls)
        assertTrue(r.fakeLock().held)

        r.journal.clear()
        r.time.advanceBy(1)
        r.journal.assertInOrder(
            "lock.release",
            "host.leaveForeground(remove=false)",
            "notifier.show(FINISHED)", // re-posted once detached, so that it can be dismissed
            "host.stopSelf"
        )
        assertEquals("the results stay on screen", TimerStatus.FINISHED, r.status)
    }

    @Test
    fun theClosingAudioIsAskedForOnlyOnce() {
        val r = rig()
        r.session.start(shortWorkout)
        r.runToTheEnd()
        assertEquals(1, r.cues.played.count { it == Cue.Celebration })
    }

    @Test
    fun stoppingDuringTheLingerCancelsTheLateNotification() {
        val r = rig()
        r.session.start(shortWorkout)
        r.runToTheEnd()
        r.session.stop() // "Exit" tapped on the results screen
        r.journal.clear()

        r.time.advanceBy(60_000)

        assertTrue("nothing may come back after Exit: ${r.journal}", r.journal.isEmpty())
        assertEquals(TimerStatus.NOT_STARTED, r.status)
    }

    @Test
    fun destroyingAfterAFinishedWorkoutKeepsTheResults() {
        val r = rig()
        r.session.start(shortWorkout)
        r.runToTheEnd()
        r.time.advanceBy(WorkoutSession.FINISH_LINGER_MS)
        r.session.destroy()
        assertEquals(TimerStatus.FINISHED, r.status)
        assertFalse(r.fakeLock().held)
    }

    @Test
    fun destroyingARunningServiceNeverLeavesTheScreenShowingADeadTimer() {
        val r = rig()
        r.session.start(shortWorkout)
        r.time.advanceTo(2_000)
        r.session.destroy()
        assertEquals(TimerStatus.NOT_STARTED, r.status)
        assertFalse(r.fakeLock().held)
        assertFalse(r.ticker.isPending)
        r.time.advanceBy(60_000)
        assertEquals(TimerStatus.NOT_STARTED, r.status)
    }

    @Test
    fun destroyingAPausedServiceDisarmsTheTimeoutAndResetsTheScreen() {
        val r = rig()
        r.session.start(shortWorkout)
        r.session.pause()
        r.session.destroy()
        assertFalse(r.alarm.armed)
        assertEquals(TimerStatus.NOT_STARTED, r.status)
    }

    // ---- the wake lock ----

    @Test
    fun theWakeLockIsCheckedOnEveryRunningTickAndOnlyThen() {
        val r = rig()
        r.session.start(shortWorkout)
        r.time.advanceTo(5_000)
        val whileRunning = r.fakeLock().renewals
        assertTrue("renewals while running: $whileRunning", whileRunning >= 5)

        r.session.pause()
        r.time.advanceBy(3_600_000)
        assertEquals("no renewal while paused", whileRunning, r.fakeLock().renewals)

        r.session.resume()
        r.runToTheEnd()
        val atTheEnd = r.fakeLock().renewals
        r.time.advanceBy(60_000)
        assertEquals("no renewal after the end", atTheEnd, r.fakeLock().renewals)
    }

    @Test
    fun aTwoHourWorkoutNeverLosesTheCpuWakeLock() {
        // 100 minutes of work; the real WakeLockHolder, on a lock the "system" drops after its timeout.
        lateinit var raw: ExpiringRawLock
        val r = rig { _, time ->
            raw = ExpiringRawLock { time.now }
            WakeLockHolder(raw) { time.now }
        }
        val config = TimerConfig(tasks = listOf(work(5_999)))
        r.session.start(config)

        var minutesChecked = 0
        while (r.time.now < 95 * 60_000L) {
            r.time.advanceBy(60_000)
            assertTrue("lock lost after ${r.time.now / 60_000} min", raw.isHeld)
            minutesChecked++
        }
        assertTrue(minutesChecked >= 90)
        assertEquals(TimerStatus.RUNNING, r.status)

        r.session.pause()
        assertFalse(raw.isHeld)
        r.time.advanceBy(30 * 60_000L)
        assertFalse("a paused workout must not take the lock back", raw.isHeld)

        r.session.resume()
        assertTrue(raw.isHeld)
    }

    // ---- failures in side effects ----

    @Test
    fun aFailingNotificationCannotStopTheClock() {
        val r = rig()
        r.notifier.failOnUpdate = true
        r.session.start(shortWorkout)
        r.runToTheEnd()
        assertEquals(TimerStatus.FINISHED, r.status)
        assertTrue(r.host.errors.any { it.startsWith("onStateChanged failed") })
        assertEquals(1, r.journal.count("notifier.show(FINISHED)"))
    }

    @Test
    fun anAudioFailureIsReportedAndTheWorkoutGoesOn() {
        val r = rig()
        r.cues.failOnBeep = true
        r.session.start(shortWorkout)
        r.runToTheEnd()
        assertEquals(TimerStatus.FINISHED, r.status)
        assertTrue(r.host.errors.any { it.startsWith("Cannot play") })
    }

    @Test
    fun aLateWakeUpIsLoggedAsAWarning() {
        val r = rig()
        r.time.jitterMs = 700
        r.session.start(shortWorkout)
        r.time.advanceTo(3_000)
        assertTrue(r.host.warnings.any { it.endsWith("ms late") })
        assertEquals(TimerStatus.RUNNING, r.status)
    }
}
