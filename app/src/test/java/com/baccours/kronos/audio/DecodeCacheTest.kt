package com.baccours.kronos.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** The part of clip loading that is concurrent: one decode per clip, failures retried after a cool-off. */
class DecodeCacheTest {

    private var clock = 0L
    private val calls = AtomicInteger()
    private val clip = shortArrayOf(1, 2, 3)

    private fun cache(decode: (String) -> ShortArray?) =
        DecodeCache(now = { clock }, retryAfterMs = 30_000, decode = { calls.incrementAndGet(); decode(it) })

    @Test
    fun aClipIsDecodedOnceAndThenServedFromTheCache() {
        val cache = cache { clip }
        val first = cache.get("go")
        assertSame(first, cache.get("go"))
        assertEquals(1, calls.get())
        assertTrue(cache.contains("go"))
        assertFalse(cache.contains("rest"))
    }

    @Test
    fun clipsAreDecodedIndependently() {
        val cache = cache { name -> shortArrayOf(name.length.toShort()) }
        assertEquals(2, cache.get("go")!![0].toInt())
        assertEquals(4, cache.get("rest")!![0].toInt())
        assertEquals(2, calls.get())
    }

    @Test
    fun manyThreadsAskingAtOnceDecodeOnlyOnce() {
        val release = CountDownLatch(1)
        val cache = cache { release.await(5, TimeUnit.SECONDS); clip }
        val threads = 8
        val start = CyclicBarrier(threads + 1)
        val results = arrayOfNulls<ShortArray>(threads)

        val workers = List(threads) { i ->
            thread { start.await(); results[i] = cache.get("go") }
        }
        start.await()
        Thread.sleep(50) // let the others queue up behind the decoding thread
        release.countDown()
        workers.forEach { it.join(5_000) }

        assertEquals(1, calls.get())
        for (r in results) assertSame(results[0], r)
        assertNotNull(results[0])
    }

    @Test
    fun aFailedDecodeIsNotRetriedDuringTheCoolOffThenIsTriedAgain() {
        var healthy = false
        val cache = cache { if (healthy) clip else null }

        assertNull(cache.get("go"))
        assertTrue(cache.recentlyFailed("go"))
        assertFalse("a failure must not be cached", cache.contains("go"))

        clock = 29_999
        assertNull(cache.get("go"))
        assertEquals("still cooling off: no new decode", 1, calls.get())

        healthy = true
        clock = 30_000
        assertFalse(cache.recentlyFailed("go"))
        assertNotNull(cache.get("go"))
        assertEquals(2, calls.get())
        assertTrue(cache.contains("go"))
    }

    @Test
    fun aDecoderThatThrowsCountsAsAFailureInsteadOfEscaping() {
        val cache = cache { throw IllegalStateException("codec died") }
        assertNull(cache.get("go"))
        assertTrue(cache.recentlyFailed("go"))
        assertFalse(cache.contains("go"))
    }

    @Test
    fun anInterruptedWaiterGivesUpWithoutMarkingTheClipAsFailed() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cache = cache { entered.countDown(); release.await(5, TimeUnit.SECONDS); clip }

        val owner = thread { cache.get("go") }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        var result: ShortArray? = clip
        var stillInterrupted = false
        val waiter = thread {
            Thread.currentThread().interrupt()
            result = cache.get("go") // finds the running decode and waits: interrupted at once
            stillInterrupted = Thread.currentThread().isInterrupted // the flag must survive
        }
        waiter.join(5_000)

        assertNull(result)
        assertTrue("the interrupt must be preserved for the caller", stillInterrupted)
        assertFalse("an interrupt is not a decoding failure", cache.recentlyFailed("go"))

        release.countDown()
        owner.join(5_000)
        assertNotNull(cache.get("go"))
        assertEquals(1, calls.get())
    }
}
