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
 * Process-wide cache for decoded mono 16-bit [SAMPLE_RATE] PCM clips.
 * Thread-safe. [pcm] is blocking and must be called off the main thread.
 */
class ClipLibrary private constructor(context: Context) {

    private val assets = context.applicationContext.assets

    /** Available asset filenames in `assets/audio`. */
    private val available: Set<String> = try {
        assets.list(ClipCatalog.DIR)?.toSet() ?: emptySet()
    } catch (e: Exception) {
        Log.e(TAG, "Cannot list voice clips", e)
        emptySet()
    }

    /** Concurrent decoding tasks deduplicated per clip name. */
    private val decoded = ConcurrentHashMap<String, FutureTask<ShortArray?>>()

    /** Single-thread executor for preloading audio clips. */
    private val preloader = ThreadPoolExecutor(
        1, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()
    ) { r -> Thread(r, "clip-preloader").apply { isDaemon = true } }
        .apply { allowCoreThreadTimeOut(true) }

    /** Timestamp (elapsedRealtime) of last decoding failure per clip. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** True if the asset file for [name] exists. */
    fun has(name: String): Boolean = name + ClipCatalog.EXTENSION in available

    /** True if [name] exists and has not recently failed decoding. */
    fun isUsable(name: String): Boolean = has(name) && !recentlyFailed(name)

    private fun recentlyFailed(name: String): Boolean {
        val failed = failedAt[name] ?: return false
        if (SystemClock.elapsedRealtime() - failed >= RETRY_AFTER_MS) {
            failedAt.remove(name, failed)
            return false
        }
        return true
    }

    /** Returns decoded PCM for [name], decoding on demand. Returns null on failure. */
    fun pcm(name: String): ShortArray? {
        if (!isUsable(name)) return null
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
            failedAt[name] = SystemClock.elapsedRealtime()
        }
        return result
    }

    /** Preloads [names] asynchronously in background. */
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

        /** Output sample rate for mono playback. */
        const val SAMPLE_RATE = PcmUtil.SAMPLE_RATE

        @Volatile
        private var instance: ClipLibrary? = null

        fun get(context: Context): ClipLibrary =
            instance ?: synchronized(this) {
                instance ?: ClipLibrary(context.applicationContext).also { instance = it }
            }
    }
}
