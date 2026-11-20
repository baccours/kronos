package com.baccours.kronos.service

import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerConfigCodec
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.TimerStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the UI reaches the service, including what happens when the system refuses. */
class WorkoutCommandsTest {

    private class FakeTransport : CommandTransport {
        val calls = ArrayList<String>()
        var startedWith: String? = null
        var failStart: RuntimeException? = null
        var failSend: RuntimeException? = null

        override fun startWorkout(encodedConfig: String) {
            failStart?.let { throw it }
            startedWith = encodedConfig
            calls += "start"
        }

        override fun send(action: String) {
            failSend?.let { throw it }
            calls += "send:$action"
        }

        override fun stopService() { calls += "stopService" }
        override fun cancelNotification() { calls += "cancelNotification" }
    }

    private val transport = FakeTransport()
    private val store = TimerStateStore()
    private val commands = WorkoutCommands(transport, store)

    private fun showing(status: TimerStatus) = store.updateState(TimerState(status = status))

    // ---- start ----

    @Test
    fun startSendsTheWorkoutInAFormTheServiceCanDecode() {
        val config = TimerConfig(sets = 3, rounds = 4, tasks = listOf(work(20), rest(10)))
        assertTrue(commands.start(config))
        assertEquals(config.sanitized(), TimerConfigCodec.decode(transport.startedWith))
    }

    @Test
    fun startReportsARefusedForegroundLaunch() {
        transport.failStart = IllegalStateException("app is in the background")
        assertFalse(commands.start(TimerConfig()))
    }

    @Test
    fun startReportsAMissingPermission() {
        transport.failStart = SecurityException("FOREGROUND_SERVICE_SPECIAL_USE")
        assertFalse(commands.start(TimerConfig()))
    }

    // ---- the simple commands ----

    @Test
    fun eachCommandSendsItsOwnAction() {
        commands.pause()
        commands.resume()
        commands.skip()
        commands.lap()
        assertEquals(
            listOf(
                "send:${TimerService.ACTION_PAUSE}",
                "send:${TimerService.ACTION_RESUME}",
                "send:${TimerService.ACTION_SKIP}",
                "send:${TimerService.ACTION_LAP}"
            ),
            transport.calls
        )
    }

    @Test
    fun aRefusedCommandDoesNotCrashTheUi() {
        transport.failSend = IllegalStateException("background start not allowed")
        commands.pause()
        commands.resume()
        commands.skip()
        commands.lap()
        assertTrue(transport.calls.isEmpty())
    }

    // ---- stop ----

    @Test
    fun stopOfARunningWorkoutLeavesTheCleanupToTheService() {
        showing(TimerStatus.RUNNING)
        commands.stop()
        assertEquals(listOf("send:${TimerService.ACTION_STOP}"), transport.calls)
        assertEquals("the service resets the screen itself", TimerStatus.RUNNING, store.timerState.value.status)
    }

    @Test
    fun stopCleansUpItselfWhenTheServiceCannotBeReached() {
        showing(TimerStatus.PAUSED)
        transport.failSend = IllegalStateException("background start not allowed")
        commands.stop()
        assertEquals(TimerStatus.NOT_STARTED, store.timerState.value.status)
        assertEquals(listOf("cancelNotification"), transport.calls)
    }

    @Test
    fun stopOfAFinishedWorkoutEndsTheLingeringServiceAndClearsTheResults() {
        showing(TimerStatus.FINISHED)
        commands.stop()
        assertEquals(listOf("stopService", "cancelNotification"), transport.calls)
        assertEquals(TimerStatus.NOT_STARTED, store.timerState.value.status)
    }

    @Test
    fun stopOfAFinishedWorkoutNeverRestartsAServiceJustToStopIt() {
        showing(TimerStatus.FINISHED)
        commands.stop()
        assertFalse(transport.calls.any { it.startsWith("send:") })
    }
}
