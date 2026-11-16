package com.baccours.kronos

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Enforces the package dependencies documented in the README. */
class LayeringTest {

    private val allowed = mapOf(
        "model" to emptySet(),
        "engine" to setOf("model"),
        "format" to setOf("model"),
        "repository" to setOf("model"),
        "audio" to setOf("engine", "model"),
        "service" to setOf("audio", "engine", "format", "model", "repository"),
        "ui" to setOf("format", "model", "repository")
    )

    /** Packages that must stay free of Android APIs (pure JVM, tested with virtual time). */
    private val pureJvm = setOf("model", "engine")

    private val importPattern = Regex("""^import com\.baccours\.kronos\.(\w+)""")

    @Test
    fun packagesOnlyDependOnTheirAllowedLayers() {
        val root = listOf("src/main/java", "app/src/main/java")
            .map { File(it, "com/baccours/kronos") }
            .first { it.isDirectory }
        val violations = ArrayList<String>()
        for ((pkg, deps) in allowed) {
            for (file in File(root, pkg).walkTopDown().filter { it.extension == "kt" }) {
                for (line in file.readLines().filter { it.startsWith("import ") }) {
                    val target = importPattern.find(line)?.groupValues?.get(1)
                    val illegal = target != null && target in allowed && target != pkg && target !in deps
                    val android = pkg in pureJvm && line.startsWith("import android.")
                    if (illegal || android) violations += "${pkg}/${file.name}: $line"
                }
            }
        }
        assertTrue("Layering violations:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
