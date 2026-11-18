package com.baccours.kronos.audio

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask

/**
 * Thread-safe cache of decoded clips, free of Android APIs so that it can be unit-tested.
 *
 * Each clip is decoded at most once, even when several threads ask for it at the same time:
 * the first caller decodes and the others wait for its result. A failed decode is never cached:
 * the clip is reported as failed for [retryAfterMs], then tried again.
 */
internal class DecodeCache(
    /** Monotonic clock (milliseconds). */
    private val now: () -> Long,
    private val retryAfterMs: Long,
    private val decode: (name: String) -> ShortArray?
) {

    /** Concurrent decoding tasks deduplicated per clip name. */
    private val decoded = ConcurrentHashMap<String, FutureTask<ShortArray?>>()

    /** Timestamp of the last decoding failure per clip. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** True if [name] is decoded or being decoded. */
    fun contains(name: String): Boolean = decoded.containsKey(name)

    /** True if the last decode of [name] failed less than [retryAfterMs] ago. */
    fun recentlyFailed(name: String): Boolean {
        val failed = failedAt[name] ?: return false
        if (now() - failed >= retryAfterMs) {
            failedAt.remove(name, failed)
            return false
        }
        return true
    }

    /** Returns decoded PCM for [name], decoding on demand. Returns null on failure. Blocking. */
    fun get(name: String): ShortArray? {
        if (recentlyFailed(name)) return null
        var owned: FutureTask<ShortArray?>? = null
        val task = decoded.computeIfAbsent(name) { FutureTask { decode(name) }.also { owned = it } }
        owned?.run() // Execute decoding outside lock if task was created here
        val result = try {
            task.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (_: Exception) {
            null
        }
        if (result == null) {
            // Mark failed so it can be retried after cooldown.
            decoded.remove(name, task)
            failedAt[name] = now()
        }
        return result
    }
}
