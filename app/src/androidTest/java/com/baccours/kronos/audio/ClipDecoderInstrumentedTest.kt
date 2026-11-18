package com.baccours.kronos.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real `MediaCodec` on a real device. A JVM test cannot say whether this phone's decoder
 * handles the app's Ogg Vorbis clips; this does, for every clip the app can ever say.
 *
 * No foreground activity is needed: nothing is played.
 */
@RunWith(AndroidJUnit4::class)
class ClipDecoderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyClipTheAppCanSayDecodesToUsableAudioOnThisDevice() {
        val library = ClipLibrary.get(context)
        val problems = ArrayList<String>()

        for (name in ClipCatalog.allClips) {
            val pcm = library.pcm(name)
            when {
                pcm == null -> problems += "$name: decoding failed"
                pcm.size < MIN_SAMPLES -> problems += "$name: only ${pcm.size} samples"
                pcm.size > MAX_SAMPLES -> problems += "$name: ${pcm.size} samples is far too long"
                pcm.all { it.toInt() == 0 } -> problems += "$name: decoded to pure silence"
            }
        }

        assertTrue(
            "${problems.size} of ${ClipCatalog.allClips.size} clips do not decode properly:\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun theDecoderReportsASaneFormatForEveryClip() {
        val problems = ArrayList<String>()

        for (name in ClipCatalog.allClips) {
            val decoded = context.assets
                .openFd("${ClipCatalog.DIR}/$name${ClipCatalog.EXTENSION}")
                .use { ClipDecoder.decode(it, name) }
            when {
                decoded == null -> problems += "$name: decoding failed"
                decoded.samples.isEmpty() -> problems += "$name: no samples"
                decoded.channels !in 1..2 -> problems += "$name: ${decoded.channels} channels"
                decoded.sampleRate !in 8_000..192_000 -> problems += "$name: ${decoded.sampleRate} Hz"
            }
        }

        assertTrue("clips with an unexpected format:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        /** 50 ms and 10 s at the library's sample rate: outside this, a clip is certainly broken. */
        const val MIN_SAMPLES = ClipLibrary.SAMPLE_RATE / 20
        const val MAX_SAMPLES = ClipLibrary.SAMPLE_RATE * 10
    }
}
