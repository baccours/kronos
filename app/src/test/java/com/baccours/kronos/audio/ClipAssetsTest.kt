package com.baccours.kronos.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every clip the app can say must exist in assets/audio. (This used to be checked at every app
 * launch, just to write a log line.) Gradle runs unit tests with the module as working directory.
 */
class ClipAssetsTest {

    private val assetsDir = File("src/main/assets/${ClipCatalog.DIR}")

    @Test
    fun everyClipTheAppCanSayHasAnAssetFile() {
        assertTrue("cannot find ${assetsDir.absolutePath}", assetsDir.isDirectory)
        val present = assetsDir.list()!!.toSet()
        val missing = ClipCatalog.allClips.filter { it + ClipCatalog.EXTENSION !in present }
        assertTrue("missing clips: $missing", missing.isEmpty())
    }

    @Test
    fun noAssetFileIsUnused() {
        val known = ClipCatalog.allClips.map { it + ClipCatalog.EXTENSION }.toSet()
        val unused = assetsDir.list()!!.filter { it !in known }
        assertTrue("unused asset files: $unused", unused.isEmpty())
    }
}
