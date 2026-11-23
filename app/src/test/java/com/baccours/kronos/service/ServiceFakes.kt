package com.baccours.kronos.service

import com.baccours.kronos.engine.Cue
import com.baccours.kronos.engine.CuePlayer
import com.baccours.kronos.engine.Speech
import com.baccours.kronos.engine.TickScheduler
import com.baccours.kronos.engine.WorkoutController
import com.baccours.kronos.model.Task
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus
import com.baccours.kronos.repository.WorkoutStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** One virtual clock shared by every scheduler, so that a whole workout runs in microseconds. */
internal class VirtualTime {
    var now = 0L

    /** Added to the due time of every task, to simulate a device that wakes up late. */
    var jitterMs = 0L

    private class Entry(val at: Long, val seq: Long, val owner: Any, val task: () -> Unit)

    private val entries = ArrayList<Entry>()
    private var seq = 0L

    fun schedule(owner: Any, delayMs: Long, task: () -> Unit) {
        cancel(owner)
        entries += Entry(now + delayMs, seq++, owner, task)
    }

    fun cancel(owner: Any) {
        entries.removeAll { it.owner === owner }
    }

    fun dueAt(owner: Any): Long? = entries.firstOrNull { it.owner === owner }?.at

    fun advanceBy(ms: Long) = advanceTo(now + ms)

    fun advanceTo(target: Long) {
        while (true) {
            val next = entries.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            if (next.at > target) break
            entries.remove(next)
            now = maxOf(now, next.at + jitterMs)
            next.task()
        }
        now = maxOf(now, target)
    }
}

/** A single-task scheduler on the shared [VirtualTime] (what the service builds on a Handler). */
internal class FakeScheduler(private val time: VirtualTime) : TickScheduler {
    val isPending: Boolean get() = dueAt != null
    val dueAt: Long? get() = time.dueAt(this)

    /** When the pending task was scheduled. */
    var scheduledAt = -1L
        private set

    override fun scheduleAfter(delayMs: Long, task: () -> Unit) {
        scheduledAt = time.now
        time.schedule(this, delayMs, task)
    }

    override fun cancel() = time.cancel(this)
}

/** Every side effect the session has on its surroundings, in order. */
internal class Journal : ArrayList<String>() {
    fun count(entry: String) = count { it == entry }

    /** Fails unless the first occurrences of [entries] appear in this order. */
    fun assertInOrder(vararg entries: String) {
        var from = 0
        for (entry in entries) {
            val at = subList(from, size).indexOf(entry)
            check(at >= 0) { "Expected '$entry' after index ${from - 1}, in: $this" }
            from += at + 1
        }
    }
}

internal class FakeHost(private val journal: Journal) : SessionHost {
    var allowForeground = true
    var stopSelfCalls = 0
    val warnings = ArrayList<String>()
    val errors = ArrayList<String>()

    override fun enterForeground(state: TimerState): Boolean {
        journal += "host.enterForeground(${state.status})"
        return allowForeground
    }

    override fun leaveForeground(removeNotification: Boolean) {
        journal += "host.leaveForeground(remove=$removeNotification)"
    }

    override fun stopSelf() {
        stopSelfCalls++
        journal += "host.stopSelf"
    }

    override fun logWarning(message: String) { warnings += message }
    override fun logError(message: String, error: Throwable) { errors += message }
}

internal class FakeNotifier(private val journal: Journal) : SessionNotifier {
    var failOnUpdate = false

    override fun show(state: TimerState) { journal += "notifier.show(${state.status})" }

    override fun updateIfChanged(state: TimerState) {
        journal += "notifier.update(${state.status})"
        if (failOnUpdate) throw IllegalStateException("notification manager is gone")
    }

    override fun cancel() { journal += "notifier.cancel" }
    override fun showPauseTimeout() { journal += "notifier.pauseTimeout" }
}

internal class FakeLock(private val journal: Journal) : HeldLock {
    var held = false
        private set
    var acquires = 0
        private set
    var renewals = 0
        private set

    override fun acquire() { held = true; acquires++; journal += "lock.acquire" }
    override fun renewIfDue() { renewals++ }
    override fun release() { held = false; journal += "lock.release" }
}

/** The pause-timeout alarm: [fire] is what the system does when the hour is over. */
internal class FakePauseAlarm(private val journal: Journal) : PauseAlarm {
    var armed = false
        private set
    var starts = 0
        private set
    var onFire: () -> Unit = {}

    override fun start() { armed = true; starts++; journal += "alarm.start" }
    override fun cancel() { armed = false; journal += "alarm.cancel" }

    fun fire() {
        if (!armed) return
        armed = false
        onFire()
    }
}

internal class RecordingCues : CuePlayer {
    val played = ArrayList<Cue>()
    val prepared = ArrayList<List<Speech>>()
    var stopSpeakingCalls = 0
    var failOnBeep = false

    override fun play(cue: Cue) {
        if (failOnBeep && cue == Cue.Beep) throw IllegalStateException("audio track is gone")
        played += cue
    }

    override fun prepare(speeches: List<Speech>) { prepared += listOf(speeches) }
    override fun stopSpeaking() { stopSpeakingCalls++ }
    override fun release() {}
}

/**
 * A [WorkoutSession] around the real controller and engine, with every Android-facing part faked
 * and all timers on one virtual clock.
 */
internal class Rig(
    lockFactory: (Journal, VirtualTime) -> HeldLock = { journal, _ -> FakeLock(journal) }
) {
    val journal = Journal()
    val time = VirtualTime()
    val host = FakeHost(journal)
    val notifier = FakeNotifier(journal)
    val lock = lockFactory(journal, time)
    val alarm = FakePauseAlarm(journal)
    val cues = RecordingCues()
    val ticker = FakeScheduler(time)
    val linger = FakeScheduler(time)
    val store = WorkoutStateStore()

    val session = WorkoutSession(host, notifier, lock, alarm, linger, store) { listener ->
        WorkoutController(cues, ticker, { time.now }, listener)
    }

    /** One entry per "could not start" event the UI would have been told about. */
    val startFailures = ArrayList<Unit>()
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    init {
        alarm.onFire = { session.endForgottenPause() }
        scope.launch { store.startFailures.collect { startFailures += it } }
    }

    val status: TimerStatus get() = store.timerState.value.status
    val state: TimerState get() = store.timerState.value

    fun close() = scope.cancel()

    /** Lets virtual time pass until the workout is over. */
    fun runToTheEnd(limitMs: Long = 24L * 3_600_000) {
        val end = time.now + limitMs
        while (status != TimerStatus.FINISHED && time.now < end) time.advanceBy(250)
        check(status == TimerStatus.FINISHED) { "Workout did not finish within $limitMs ms; now $status" }
    }

    /** Skips segments until the workout is over (works while paused too). */
    fun skipToTheEnd() {
        var guard = 0
        while (status != TimerStatus.FINISHED) {
            session.skip()
            check(++guard < 50) { "skip never finished the workout" }
        }
    }
}

internal fun work(seconds: Int) = Task(type = TaskType.WORK, durationSeconds = seconds)
internal fun rest(seconds: Int) = Task(type = TaskType.REST, durationSeconds = seconds)

/** 10 s get-ready + 3 s work + 2 s rest: finishes 15 s after the start. */
internal val shortWorkout = TimerConfig(tasks = listOf(work(3), rest(2)))

/** Open-ended: runs until the user ends it. */
internal val stopwatch = TimerConfig(mode = TimerMode.FOR_TIME, tasks = listOf(work(0)))
