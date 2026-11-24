package com.baccours.kronos.audio

import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baccours.kronos.MainActivity
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real audio output. These tests play the "go" clip through the phone's speaker, so keep the media
 * volume up and the phone unlocked, with no call in progress.
 *
 * The app is kept in the foreground: from Android 15 an app in the background is refused audio focus.
 */
@RunWith(AndroidJUnit4::class)
class ClipPlaybackInstrumentedTest {

    @get:Rule
    val foreground = ActivityScenarioRule(MainActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val sonification = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun clipDurationMs(samples: Int) = samples * 1000L / ClipLibrary.SAMPLE_RATE

    @Test
    fun theTrackReportsItsLastFrameOnTheMainThreadOnceTheClipHasPlayed() {
        val pcm = checkNotNull(ClipLibrary.get(context).pcm("go")) { "the clip 'go' did not decode" }
        val track = checkNotNull(createStaticTrack(pcm, speech)) { "the AudioTrack could not be created" }
        val clip = AudioTrackClip(track, pcm.size, mainHandler)
        val clipMs = clipDurationMs(pcm.size)

        val reached = CountDownLatch(1)
        var calledOnMainThread = false
        var startedAt: Long
        var elapsedMs = -1L
        try {
            instrumentation.runOnMainSync {
                startedAt = SystemClock.elapsedRealtime()
                clip.start {
                    calledOnMainThread = Looper.myLooper() === Looper.getMainLooper()
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    reached.countDown()
                }
            }
            assertTrue(
                "the end-of-clip notification never arrived (the announcer would rely on its fallback timer)",
                reached.await(clipMs + 3_000, TimeUnit.MILLISECONDS)
            )
        } finally {
            instrumentation.runOnMainSync { clip.release() }
        }

        assertTrue("the notification must be delivered on the main thread", calledOnMainThread)
        assertTrue(
            "notified after $elapsedMs ms for a $clipMs ms clip",
            elapsedMs in (clipMs * 8 / 10)..(clipMs + 1_500)
        )
    }

    @Test
    fun theAnnouncerPlaysACueToTheEndAndIsThenIdle() {
        val library = ClipLibrary.get(context)
        assertTrue("the clip 'go' is not usable", library.isUsable("go"))
        val clipMs = clipDurationMs(checkNotNull(library.pcm("go")).size) // also warms the cache

        lateinit var focus: AudioFocusController
        lateinit var announcer: ClipAnnouncer
        var accepted = false
        instrumentation.runOnMainSync {
            focus = AudioFocusController(context, mainHandler, sonification)
            announcer = ClipAnnouncer(context, focus, library)
            focus.bindTo(announcer)
            accepted = announcer.say(Cue.Speak(Speech.Go))
        }
        try {
            assumeTrue("audio focus was refused: is a call in progress?", accepted)

            val deadline = SystemClock.elapsedRealtime() + clipMs + ANNOUNCEMENT_SLACK_MS
            var speaking = true
            while (speaking && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { speaking = announcer.isSpeaking }
                if (speaking) Thread.sleep(POLL_MS)
            }
            assertTrue("the announcer was still speaking $ANNOUNCEMENT_SLACK_MS ms after the clip ended", !speaking)
        } finally {
            instrumentation.runOnMainSync {
                announcer.release()
                focus.release()
            }
        }
    }

    private companion object {
        const val POLL_MS = 25L
        const val ANNOUNCEMENT_SLACK_MS = 3_000L
    }
}
