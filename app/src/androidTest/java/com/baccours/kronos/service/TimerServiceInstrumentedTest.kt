package com.baccours.kronos.service

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baccours.kronos.MainActivity
import com.baccours.kronos.appContainer
import com.baccours.kronos.model.AppSettings
import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real [TimerService] driven through [WorkoutCommands], the way the UI and the notification do.
 * The lifecycle rules (when the service starts, stays and stops itself) are decided by the
 * platform, so they are checked against the platform. The branching logic is covered, with a
 * virtual clock, by `WorkoutSessionTest`.
 *
 * The app is kept in the foreground, as a foreground-service start requires it. Keep the phone
 * unlocked. Beeps and voice are muted for the duration of the tests.
 */
@RunWith(AndroidJUnit4::class)
class TimerServiceInstrumentedTest {

    @get:Rule
    val foreground = ActivityScenarioRule(MainActivity::class.java)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = context.appContainer
    private val store = container.timerStateStore
    private val settingsStore = container.settingsStore
    private val commands = WorkoutCommands(context, store)
    private val state: TimerState get() = store.timerState.value

    private lateinit var savedSettings: AppSettings

    /** 10 s get-ready, then 5 minutes: still running when the test is done with it. */
    private val longTimer = TimerConfig(tasks = listOf(Task("w", TaskType.WORK, 300)))

    /** 10 s get-ready, then 1 s: over as soon as the get-ready is skipped. */
    private val oneSecond = TimerConfig(tasks = listOf(Task("w", TaskType.WORK, 1)))

    private val openEnded = TimerConfig(mode = TimerMode.FOR_TIME)

    @Before
    fun muteAndStartClean() {
        savedSettings = settingsStore.current
        settingsStore.set(savedSettings.copy(beeps = false, voice = false))
        endWhateverIsLeft()
    }

    @After
    fun cleanUp() {
        endWhateverIsLeft()
        settingsStore.set(savedSettings)
    }

    private fun endWhateverIsLeft() {
        commands.stop()
        await("the screen to be reset") { state.status == TimerStatus.NOT_STARTED }
        await("the service to end", 8_000) { !serviceRunning() }
    }

    @Test
    fun startPauseResumeAndStopDriveTheRealService() {
        assertTrue(commands.start(longTimer))
        await("the workout to run") { state.status == TimerStatus.RUNNING }
        assertEquals(longTimer.sanitized(), state.config)
        assertTrue("the service is running", serviceRunning())

        commands.pause()
        await("the pause") { state.status == TimerStatus.PAUSED }
        val pausedAt = state.segmentElapsedMs
        SystemClock.sleep(1_200)
        assertEquals("the clock is frozen while paused", pausedAt, state.segmentElapsedMs)

        commands.resume()
        await("the resume") { state.status == TimerStatus.RUNNING }
        await("the clock to run again") { state.segmentElapsedMs > pausedAt }

        commands.stop()
        await("the screen to be reset") { state.status == TimerStatus.NOT_STARTED }
        await("the service to end") { !serviceRunning() }
    }

    @Test
    fun aSecondStartWhileRunningChangesNothing() {
        assertTrue(commands.start(longTimer))
        await("the workout to run") { state.status == TimerStatus.RUNNING }
        val first = state.config

        assertTrue(commands.start(TimerConfig(sets = 3, tasks = listOf(Task("x", TaskType.WORK, 90)))))
        SystemClock.sleep(1_000)

        assertEquals(first, state.config)
        assertEquals(TimerStatus.RUNNING, state.status)
        assertTrue(serviceRunning())
    }

    @Test
    fun skipAndLapReachTheEngine() {
        assertTrue(commands.start(openEnded))
        await("the get-ready phase") { state.status == TimerStatus.RUNNING && state.phase == Phase.READY }

        commands.skip()
        await("the work phase") { state.phase == Phase.WORK }

        commands.lap()
        await("the lap") { state.laps.size == 1 }
    }

    @Test
    fun aFinishedWorkoutKeepsItsResultsAfterTheServiceHasEnded() {
        assertTrue(commands.start(oneSecond))
        await("the workout to run") { state.status == TimerStatus.RUNNING }
        commands.skip() // skip the get-ready
        await("the end of the workout", 8_000) { state.status == TimerStatus.FINISHED }
        assertTrue("the service lingers for the closing audio", serviceRunning())

        await("the service to end by itself", 10_000) { !serviceRunning() }
        assertEquals("the results stay on screen", TimerStatus.FINISHED, state.status)

        commands.stop() // "Exit" on the results screen
        await("the screen to be reset") { state.status == TimerStatus.NOT_STARTED }
    }

    @Test
    fun commandsForAWorkoutThatDoesNotExistLeaveNoIdleServiceBehind() {
        commands.pause()
        commands.resume()
        commands.skip()
        commands.lap()
        SystemClock.sleep(2_000)
        assertFalse(serviceRunning())
        assertEquals(TimerStatus.NOT_STARTED, state.status)
    }

    @Test
    fun stopWithNothingRunningEndsCleanly() {
        commands.stop()
        SystemClock.sleep(2_000)
        assertFalse(serviceRunning())
        assertEquals(TimerStatus.NOT_STARTED, state.status)
    }

    // ---- helpers ----

    private fun await(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Timed out after $timeoutMs ms waiting for $what (status ${state.status}, phase ${state.phase})")
    }

    /** From Android 8 this lists only the caller's own services, which is all we need. */
    @Suppress("DEPRECATION")
    private fun serviceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == TimerService::class.java.name }
    }
}
