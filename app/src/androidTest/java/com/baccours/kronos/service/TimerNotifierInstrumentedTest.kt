package com.baccours.kronos.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baccours.kronos.R
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the user sees in the notification shade: the chronometer (a sign error there shows a
 * wrong time on the lock screen and is easy to miss by hand), the buttons for each state, and
 * when the notification is re-posted.
 */
@RunWith(AndroidJUnit4::class)
class TimerNotifierInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = TimerNotifier(context)

    private val timer = TimerConfig(tasks = listOf(Task("w", TaskType.WORK, 30), Task("r", TaskType.REST, 10)))
    private val forTime = TimerConfig(mode = TimerMode.FOR_TIME)

    private fun running(
        config: TimerConfig,
        phase: Phase = Phase.WORK,
        segmentSeconds: Int = 30,
        elapsedMs: Long = 5_000,
        workIndex: Int = 1,
        segmentIndex: Int = 1
    ) = TimerState(
        status = TimerStatus.RUNNING,
        config = config,
        phase = phase,
        workIndex = workIndex,
        segmentIndex = segmentIndex,
        segmentSeconds = segmentSeconds,
        segmentElapsedMs = elapsedMs
    )

    @Before
    fun setUp() {
        notifier.createChannel()
    }

    @After
    fun cleanUp() {
        manager.cancel(TimerNotifier.NOTIFICATION_ID)
        manager.cancel(TimerNotifier.TIMEOUT_NOTIFICATION_ID)
    }

    // ---- what a notification contains ----

    @Test
    fun aCountdownShowsTheTimeLeftOnACountingDownChronometer() {
        val before = System.currentTimeMillis()
        val n = notifier.buildForForeground(running(timer, segmentSeconds = 30, elapsedMs = 5_000))
        val after = System.currentTimeMillis()

        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // 30 s segment, 5 s gone: it ends 25 s from now.
        assertTrue("when=${n.`when`}", n.`when` in (before + 25_000)..(after + 25_000))
    }

    @Test
    fun theGetReadyPhaseAlsoCountsDown() {
        val before = System.currentTimeMillis()
        val n = notifier.buildForForeground(
            running(timer, phase = Phase.READY, segmentSeconds = 10, elapsedMs = 2_000, workIndex = 0, segmentIndex = 0)
        )
        val after = System.currentTimeMillis()
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue("when=${n.`when`}", n.`when` in (before + 8_000)..(after + 8_000))
    }

    @Test
    fun anOpenEndedWorkSegmentCountsTheTimeElapsed() {
        val before = System.currentTimeMillis()
        val n = notifier.buildForForeground(running(forTime, segmentSeconds = 0, elapsedMs = 7_000))
        val after = System.currentTimeMillis()

        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // Started 7 s ago.
        assertTrue("when=${n.`when`}", n.`when` in (before - 7_000)..(after - 7_000))
    }

    @Test
    fun aCappedForTimeSegmentStillShowsTheTimeElapsed() {
        val before = System.currentTimeMillis()
        val n = notifier.buildForForeground(running(forTime, segmentSeconds = 60, elapsedMs = 5_000))
        val after = System.currentTimeMillis()
        assertFalse(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue("when=${n.`when`}", n.`when` in (before - 5_000)..(after - 5_000))
    }

    @Test
    fun theButtonsFollowTheState() {
        val pause = context.getString(R.string.action_pause)
        val resume = context.getString(R.string.action_resume)
        val round = context.getString(R.string.action_round)
        val stop = context.getString(R.string.action_stop)

        assertEquals(listOf(pause, stop), titles(notifier.buildForForeground(running(timer))))
        assertEquals(
            "a lap button while the stopwatch runs",
            listOf(round, pause, stop),
            titles(notifier.buildForForeground(running(forTime, segmentSeconds = 0)))
        )
        assertEquals(
            "no lap button during the get-ready",
            listOf(pause, stop),
            titles(notifier.buildForForeground(running(forTime, phase = Phase.READY, segmentSeconds = 10, workIndex = 0)))
        )
        assertEquals(listOf(resume, stop), titles(notifier.buildForForeground(running(timer).copy(status = TimerStatus.PAUSED))))
        assertEquals(emptyList<String>(), titles(notifier.buildForForeground(running(timer).copy(status = TimerStatus.FINISHED))))
    }

    @Test
    fun aPausedWorkoutShowsNoRunningClock() {
        val n = notifier.buildForForeground(running(timer).copy(status = TimerStatus.PAUSED))
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun aFinishedWorkoutCanBeDismissed() {
        val n = notifier.buildForForeground(running(timer).copy(status = TimerStatus.FINISHED))
        assertEquals(0, n.flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(context.getString(R.string.notification_finished_title), n.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun aRunningWorkoutCannotBeSwipedAway() {
        val n = notifier.buildForForeground(running(timer))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull("tapping it must open the app", n.contentIntent)
    }

    // ---- when it is posted ----

    @Test
    fun itIsOnlyRepostedWhenSomethingBesideTheChronometerChanged() {
        assumeNotificationsAllowed()
        val first = running(timer, workIndex = 1, segmentIndex = 1)
        notifier.show(first)
        val originalTitle = awaitPosted(TimerNotifier.NOTIFICATION_ID).extras.getString(Notification.EXTRA_TITLE)

        // Same segment, same status: the system chronometer does the ticking, nothing is re-posted.
        notifier.updateIfChanged(first.copy(workIndex = 2, segmentElapsedMs = 9_000))
        SystemClock.sleep(400)
        assertEquals(
            originalTitle,
            awaitPosted(TimerNotifier.NOTIFICATION_ID).extras.getString(Notification.EXTRA_TITLE)
        )

        // A new segment is re-posted.
        notifier.updateIfChanged(first.copy(workIndex = 2, segmentIndex = 2))
        awaitUntil("the new segment to be posted") {
            posted(TimerNotifier.NOTIFICATION_ID)?.extras?.getString(Notification.EXTRA_TITLE) != originalTitle
        }
    }

    @Test
    fun pausingRepostsWithTheResumeButton() {
        assumeNotificationsAllowed()
        val state = running(timer)
        notifier.show(state)
        awaitPosted(TimerNotifier.NOTIFICATION_ID)

        notifier.updateIfChanged(state.copy(status = TimerStatus.PAUSED))

        val resume = context.getString(R.string.action_resume)
        awaitUntil("the paused notification") {
            posted(TimerNotifier.NOTIFICATION_ID)?.let { titles(it).contains(resume) } == true
        }
    }

    @Test
    fun cancelRemovesTheNotification() {
        assumeNotificationsAllowed()
        notifier.show(running(timer))
        awaitPosted(TimerNotifier.NOTIFICATION_ID)
        notifier.cancel()
        awaitUntil("the notification to go") { posted(TimerNotifier.NOTIFICATION_ID) == null }
    }

    @Test
    fun aNotStartedStateIsNeverPosted() {
        assumeNotificationsAllowed()
        notifier.show(TimerState())
        SystemClock.sleep(400)
        assertEquals(null, posted(TimerNotifier.NOTIFICATION_ID))
    }

    @Test
    fun theForgottenPauseNoticeIsSeparateAndDismissible() {
        assumeNotificationsAllowed()
        notifier.show(running(timer))
        notifier.cancel() // what the session does just before it
        notifier.showPauseTimeout()

        val notice = awaitPosted(TimerNotifier.TIMEOUT_NOTIFICATION_ID)
        assertEquals(context.getString(R.string.notification_timeout_title), notice.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notice.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(0, notice.flags and Notification.FLAG_ONGOING_EVENT)
    }

    // ---- helpers ----

    private fun titles(n: Notification): List<String> = n.actions?.map { it.title.toString() } ?: emptyList()

    private fun posted(id: Int): Notification? =
        manager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun awaitPosted(id: Int): Notification {
        awaitUntil("notification $id to be posted") { posted(id) != null }
        return posted(id)!!
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for $what")
    }

    private fun assumeNotificationsAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } catch (_: SecurityException) {
                // Reported by the assumption below.
            }
        }
        assumeTrue(
            "Notifications are blocked for the app on this device",
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }
}
