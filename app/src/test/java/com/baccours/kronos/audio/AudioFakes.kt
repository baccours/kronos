package com.baccours.kronos.audio

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Deterministic stand-ins for the Android side of the audio code.
 *
 * Nothing runs by itself: the test decides when the "worker thread" runs a task and when the
 * "main thread" runs what was posted to it, which makes every interleaving reproducible.
 */

/** A main thread with virtual time. */
internal class FakeMainThread : MainThread {
    var now = 0L
        private set
    private var seq = 0

    private class Entry(val at: Long, val seq: Int, val task: Runnable)

    private val entries = ArrayList<Entry>()

    val pending: Int get() = entries.size

    override fun post(task: Runnable) { entries += Entry(now, seq++, task) }
    override fun postDelayed(task: Runnable, delayMs: Long) { entries += Entry(now + delayMs, seq++, task) }
    override fun cancel(task: Runnable) { entries.removeAll { it.task === task } }

    /** Runs everything due now, in order, including work posted meanwhile. */
    fun runDue() = advanceBy(0)

    /** Moves time forward by [ms], running tasks in time order on the way. */
    fun advanceBy(ms: Long) {
        val target = now + ms
        while (true) {
            val next = entries.filter { it.at <= target }
                .minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            entries.remove(next)
            now = maxOf(now, next.at)
            next.task.run()
        }
        now = target
    }
}

/** A single worker thread that runs queued tasks only when told to. */
internal class FakeWorker : AbstractExecutorService() {
    private val tasks = ArrayDeque<Runnable>()
    private var shutDown = false

    val pending: Int get() = tasks.size

    override fun execute(command: Runnable) {
        if (shutDown) throw RejectedExecutionException("worker shut down")
        tasks.addLast(command)
    }

    fun runNext() { tasks.removeFirstOrNull()?.run() }
    fun runAll() { while (tasks.isNotEmpty()) runNext() }

    override fun shutdown() { shutDown = true }
    override fun shutdownNow(): MutableList<Runnable> {
        shutDown = true
        return tasks.toMutableList().also { tasks.clear() }
    }
    override fun isShutdown() = shutDown
    override fun isTerminated() = shutDown && tasks.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
}

internal class FakeClip(override val frames: Int) : PreparedClip {
    var started = false
    var released = false
    var failToStart = false
    var failToRelease = false
    private var onLastFrame: (() -> Unit)? = null

    override fun start(onLastFrame: () -> Unit) {
        if (failToStart) throw IllegalStateException("AudioTrack is dead")
        started = true
        this.onLastFrame = onLastFrame
    }

    override fun release() {
        released = true
        if (failToRelease) throw IllegalStateException("already released")
    }

    /** The audio hardware reports that the last frame was played. */
    fun reachLastFrame() = onLastFrame?.invoke()
}

internal class FakeClipFactory : ClipFactory {
    val created = ArrayList<FakeClip>()
    var returnsNull = false
    var throws = false
    var nextFailsToStart = false

    override fun create(pcm: ShortArray): PreparedClip? {
        if (throws) throw IllegalStateException("cannot create AudioTrack")
        if (returnsNull) return null
        return FakeClip(pcm.size).also {
            it.failToStart = nextFailsToStart
            nextFailsToStart = false
            created += it
        }
    }
}

/** Every clip exists and decodes to three samples, except the ones listed. */
internal class FakeClipSource : ClipSource {
    val missing = HashSet<String>()
    val undecodable = HashSet<String>()
    val preloaded = ArrayList<Collection<String>>()

    /** How many times the worker asked for decoded audio. */
    var pcmCalls = 0

    override fun isUsable(name: String) = name !in missing
    override fun pcm(name: String): ShortArray? {
        pcmCalls++
        return if (name in undecodable) null else shortArrayOf(1, 2, 3)
    }
    override fun preload(names: Collection<String>) { preloaded += names }
}

internal class FakeFocusGate : FocusGate {
    var grant = true
    var requests = 0
    val releaseAfterCalls = ArrayList<Long>()

    override fun request(): Boolean { requests++; return grant }
    override fun releaseAfter(delayMs: Long) { releaseAfterCalls += delayMs }
}

internal class FakeFocusSystem : FocusSystem {
    var grant = true
    var requests = 0
    var abandons = 0
    private var onLoss: (() -> Unit)? = null

    override fun request(onLoss: () -> Unit): Boolean {
        requests++
        this.onLoss = onLoss
        return grant
    }

    override fun abandon() { abandons++ }

    /** Another app (an incoming call, say) takes the audio focus. */
    fun loseFocus() = onLoss?.invoke()
}
