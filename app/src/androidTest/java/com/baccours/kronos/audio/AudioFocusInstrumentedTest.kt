package com.baccours.kronos.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baccours.kronos.MainActivity
import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.Speech
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The real audio-focus system, with a "witness" standing in for another app (music, a call):
 * it holds the focus, and the system tells it when we take the focus and when we give it back.
 *
 * Keep the phone unlocked with no call in progress; the announcer test plays the "go" clip aloud.
 * Both the witness and the code under test live in this app's process, which the system treats
 * as two separate focus owners.
 *
 * The witness asks to be told when it is ducked: from Android 8 the system otherwise ducks an app
 * silently, without any callback, and there would be nothing to observe.
 *
 * What the system sends, and in which order, differs between Android versions and manufacturers.
 * So the tests never assume "the first event is X": they wait for the one event that proves the
 * behavior, ignore the rest, and print a timestamped chronology of everything when they fail.
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusInstrumentedTest {

    @get:Rule
    val foreground = ActivityScenarioRule(MainActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val sonification = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val startedAt = SystemClock.elapsedRealtime()

    /** Everything that happened, in order, with timestamps: shown when a test fails. */
    private val chronology = CopyOnWriteArrayList<String>()

    /** Focus changes delivered to the witness, not yet consumed by the test. */
    private val witnessEvents = LinkedBlockingQueue<Int>()

    private val witness by lazy { // built lazily: AudioFocusRequest does not exist before API 26
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(sonification)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                note("witness was told: ${describe(change)}")
                witnessEvents.add(change)
            }, mainHandler)
            .build()
    }

    private lateinit var focus: AudioFocusController

    @Before
    fun setUp() {
        assumeTrue("needs AudioFocusRequest (API 26)", android.os.Build.VERSION.SDK_INT >= 26)
        witnessEvents.clear()
        instrumentation.runOnMainSync { focus = AudioFocusController(context, mainHandler, sonification) }
    }

    @After
    fun tearDown() {
        if (android.os.Build.VERSION.SDK_INT < 26) return // setUp skipped the test
        audioManager.abandonAudioFocusRequest(witness)
        instrumentation.runOnMainSync { focus.release() }
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun note(text: String) {
        chronology += "+${SystemClock.elapsedRealtime() - startedAt} ms  $text"
    }

    private fun describe(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> "UNKNOWN($change)"
    }

    private fun why(message: String) = "$message\nWhat happened:\n" + chronology.joinToString("\n") { "  $it" }

    private fun witnessTakesTheFocus() {
        val result = audioManager.requestAudioFocus(witness)
        note("witness requested the focus: result=$result")
        assumeTrue(
            "the system refused the witness's request (call in progress? app not in the foreground?)",
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        )
    }

    private fun requestFocus(): Boolean {
        var granted = false
        instrumentation.runOnMainSync { granted = focus.request() }
        note("controller requested the focus: granted=$granted")
        return granted
    }

    private fun releaseFocus() {
        instrumentation.runOnMainSync { focus.release() }
        note("controller released the focus")
    }

    /**
     * Waits for a witness event for which [matches] is true, skipping any other event.
     * Returns it, or null on timeout.
     */
    private fun awaitWitness(timeoutMs: Long = EVENT_TIMEOUT_MS, matches: (Int) -> Boolean): Int? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) return null
            val change = witnessEvents.poll(left, TimeUnit.MILLISECONDS) ?: return null
            if (matches(change)) return change
        }
    }

    /** Lets the system finish delivering what the previous step caused, then forgets it. */
    private fun startFresh() {
        Thread.sleep(SETTLE_MS)
        witnessEvents.clear()
        note("---- previous exchange over, events forgotten ----")
    }

    private fun isLoss(change: Int) = change < 0

    // ---- Tests -------------------------------------------------------------------------------

    @Test
    fun anotherAppTakingTheFocusIsReportedToTheController() {
        val lost = CountDownLatch(1)
        instrumentation.runOnMainSync {
            focus.onLost = {
                note("controller was told the focus is lost")
                lost.countDown()
            }
        }
        assumeTrue("focus was refused", requestFocus())

        witnessTakesTheFocus() // "an incoming call" or another app starting playback

        assertTrue(
            why("the controller was never told the focus was lost"),
            lost.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun afterLosingTheFocusTheControllerAsksTheSystemAgain() {
        val lost = CountDownLatch(1)
        instrumentation.runOnMainSync {
            focus.onLost = {
                note("controller was told the focus is lost")
                lost.countDown()
            }
        }
        assumeTrue("focus was refused", requestFocus())
        witnessTakesTheFocus()
        assertTrue(why("the controller was never told the focus was lost"), lost.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        startFresh()

        assertTrue(why("a new request must be granted"), requestFocus())

        // If the controller had kept believing it still held the focus, it would not have asked
        // the system, and the witness would never hear that someone else took the focus from it.
        assertNotNull(
            why("the witness was never told that we took the focus: the controller did not ask the system again"),
            awaitWitness { isLoss(it) }
        )
    }

    @Test
    fun releasingTheFocusGivesItBackToTheOtherApp() {
        witnessTakesTheFocus()
        assumeTrue("focus was refused", requestFocus())
        assertNotNull(why("the witness should lose the focus while we hold it"), awaitWitness { isLoss(it) })
        startFresh()

        releaseFocus()

        assertNotNull(
            why("the witness should get the focus back (this is how the user's music resumes)"),
            awaitWitness { it == AudioManager.AUDIOFOCUS_GAIN }
        )
    }

    @Test
    fun theAnnouncerGivesTheFocusBackOnceTheCueHasBeenSpoken() {
        val library = ClipLibrary.get(context)
        assertTrue("the clip 'go' is not usable", library.isUsable("go"))
        library.pcm("go") // warm the cache: the test is about focus, not decoding

        witnessTakesTheFocus()
        lateinit var announcer: ClipAnnouncer
        var accepted = false
        instrumentation.runOnMainSync {
            announcer = ClipAnnouncer(context, focus, library)
            focus.bindTo(announcer)
            accepted = announcer.say(Cue.Speak(Speech.Go))
        }
        note("announcer was asked to say 'go': accepted=$accepted")
        try {
            assumeTrue("audio focus was refused", accepted)
            assertNotNull(why("the witness should lose the focus while the cue is spoken"), awaitWitness { isLoss(it) })

            // The clip ends, then the controller waits its tail before handing the focus back.
            // A GAIN that arrives while the announcer is still speaking is not the one we are after.
            val regained = awaitWitness(SPEECH_AND_TAIL_TIMEOUT_MS) { change ->
                var speaking = true
                instrumentation.runOnMainSync { speaking = announcer.isSpeaking }
                change == AudioManager.AUDIOFOCUS_GAIN && !speaking
            }
            assertNotNull(why("the focus was never given back after the cue"), regained)
        } finally {
            instrumentation.runOnMainSync { announcer.release() }
        }
    }

    private companion object {
        const val EVENT_TIMEOUT_MS = 3_000L
        const val SPEECH_AND_TAIL_TIMEOUT_MS = 6_000L
        const val SETTLE_MS = 500L
    }
}
