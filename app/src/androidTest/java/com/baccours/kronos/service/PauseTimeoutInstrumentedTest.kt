package com.baccours.kronos.service

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The real AlarmManager behind the "forgotten pause ends the workout" rule, with a 1-second
 * timeout instead of an hour (nobody can wait an hour for this). The system treats a
 * non-exact alarm as "not before about 5 s", hence the generous waits.
 */
@RunWith(AndroidJUnit4::class)
class PauseTimeoutInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fired = AtomicInteger()
    private val firedOn = AtomicReference<Thread>()
    private val firstFire = CountDownLatch(1)
    private lateinit var timeout: PauseTimeout

    @Before
    fun setUp() {
        timeout = PauseTimeout(
            context,
            Handler(Looper.getMainLooper()),
            onTimeout = {
                firedOn.set(Thread.currentThread())
                fired.incrementAndGet()
                firstFire.countDown()
            },
            timeoutMs = 1_000
        )
    }

    @After
    fun tearDown() = timeout.cancel()

    @Test
    fun theDefaultIsOneHour() {
        assertEquals(3_600_000L, PauseTimeout.TIMEOUT_MS)
    }

    @Test
    fun itFiresOnTheMainThreadOnceTheTimeIsUp() {
        timeout.start()
        assertTrue("the alarm never fired", firstFire.await(40, TimeUnit.SECONDS))
        assertSame("the callback must run where the service logic lives", Looper.getMainLooper().thread, firedOn.get())
    }

    @Test
    fun cancelPreventsItFromFiring() {
        timeout.start()
        timeout.cancel()
        assertFalse("fired despite cancel", firstFire.await(12, TimeUnit.SECONDS))
        assertEquals(0, fired.get())
    }

    @Test
    fun startingAgainReplacesThePendingAlarmInsteadOfAddingOne() {
        // A second "pause" (e.g. a double tap on the notification) must not end the workout twice.
        timeout.start()
        timeout.start()
        assertTrue(firstFire.await(40, TimeUnit.SECONDS))
        Thread.sleep(8_000)
        assertEquals(1, fired.get())
    }
}
