package com.baccours.kronos.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Decoded voice clips, kept for the life of the app process so that a clip is decoded at most once,
 * however many workouts (and [ClipAnnouncer]s) use it. Each clip is mono 16-bit PCM at
 * [SAMPLE_RATE]. Decoding is slow (about 250 ms per clip on some devices), hence [preload].
 *
 * Thread-safe. [pcm] blocks while a clip is being decoded: call it off the main thread.
 */
class ClipLibrary private constructor(context: Context) {

    private val assets = context.applicationContext.assets

    /** File names (with extension) found in assets/audio. */
    private val available: Set<String> = try {
        assets.list(ClipCatalog.DIR)?.toSet() ?: emptySet()
    } catch (e: Exception) {
        Log.e(TAG, "Cannot list voice clips", e)
        emptySet()
    }

    /** One task per clip: different clips decode in parallel, a clip wanted twice is decoded once. */
    private val decoded = ConcurrentHashMap<String, FutureTask<ShortArray?>>()

    /** Single background thread for [preload]; it exits when idle. */
    private val preloader = ThreadPoolExecutor(
        1, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()
    ) { r -> Thread(r, "clip-preloader").apply { isDaemon = true } }
        .apply { allowCoreThreadTimeOut(true) }

    /** When (elapsedRealtime) a clip last failed to decode. A failed clip is retried after [RETRY_AFTER_MS]. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    // (Whether every clip the app can say exists is checked by ClipAssetsTest, not at every launch.)

    /** True when the asset file for the clip [name] (no extension) exists. */
    fun has(name: String): Boolean = name + ClipCatalog.EXTENSION in available

    /** True when [name] exists and did not fail to decode recently, i.e. it can be expected to play. */
    fun isUsable(name: String): Boolean = has(name) && !recentlyFailed(name)

    private fun recentlyFailed(name: String): Boolean {
        val failed = failedAt[name] ?: return false
        if (SystemClock.elapsedRealtime() - failed >= RETRY_AFTER_MS) {
            failedAt.remove(name, failed)
            return false
        }
        return true
    }

    /** Decoded PCM of clip [name], decoding it on first use; null when missing or undecodable. */
    fun pcm(name: String): ShortArray? {
        if (!isUsable(name)) return null
        var owned: FutureTask<ShortArray?>? = null
        val task = decoded.computeIfAbsent(name) { FutureTask { decode(name) }.also { owned = it } }
        owned?.run() // we created the task, so we decode (outside any lock); others just wait on it
        val result = try {
            task.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (_: Exception) {
            null
        }
        if (result == null) {
            // Do not cache the failure for the life of the process: forget it, and retry later.
            decoded.remove(name, task)
            failedAt[name] = SystemClock.elapsedRealtime()
        }
        return result
    }

    /** Decodes [names] in the background, in this order, skipping clips already requested. */
    fun preload(names: Collection<String>) {
        val todo = names.filter { isUsable(it) && !decoded.containsKey(it) }
        if (todo.isEmpty()) return
        preloader.execute { for (name in todo) pcm(name) }
    }

    private fun decode(name: String): ShortArray? = try {
        assets.openFd("${ClipCatalog.DIR}/$name${ClipCatalog.EXTENSION}").use { afd ->
            ClipDecoder.decode(afd, name)
        }?.let {
            PcmUtil.resample(PcmUtil.toMono(it.samples, it.channels), it.sampleRate, SAMPLE_RATE)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot load $name", e)
        null
    }

    companion object {
        private const val TAG = "ClipLibrary"
        private const val RETRY_AFTER_MS = 30_000L

        /** Everything is played as mono at this rate (the usual native output rate). */
        const val SAMPLE_RATE = PcmUtil.SAMPLE_RATE

        @Volatile
        private var instance: ClipLibrary? = null

        fun get(context: Context): ClipLibrary =
            instance ?: synchronized(this) {
                instance ?: ClipLibrary(context.applicationContext).also { instance = it }
            }
    }
}
