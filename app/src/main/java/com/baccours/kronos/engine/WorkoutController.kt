package com.baccours.kronos.engine

import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus

/** Plays [Cue]s. Implemented by the Android audio layer; faked in tests. */
interface CuePlayer {
    fun play(cue: Cue)

    /** Gets what the announcements of an upcoming workout need ready (e.g. decodes clips). */
    fun prepare(speeches: List<Speech>)

    fun stopSpeaking()

    fun release()
}

/** Runs one task at a later time. Scheduling again replaces the pending task. */
interface TickScheduler {
    fun scheduleAfter(delayMs: Long, task: () -> Unit)

    fun cancel()
}

/**
 * Drives a [TimerEngine]: owns it, schedules its ticks, plays its cues and reports its state.
 * Knows nothing about Android (the clock, scheduler, audio and reporting are all injected),
 * so the whole run loop, including how ticks are scheduled, can be tested with virtual time.
 *
 * Must be used from a single thread (the main thread in the app).
 */
class WorkoutController(
    private val cues: CuePlayer,
    private val scheduler: TickScheduler,
    /** A monotonic clock in milliseconds (SystemClock.elapsedRealtime in the app). */
    private val clock: () -> Long,
    private val listener: Listener
) {

    interface Listener {
        /** Every new state, before it is finished with [onFinished]. */
        fun onStateChanged(state: TimerState)

        /** The workout is over (after its last [onStateChanged]). */
        fun onFinished(state: TimerState)

        /** A tick ran much later than planned, e.g. because the device was asleep. */
        fun onLateTick(lateMs: Long) {}

        /** Playing a cue failed; the workout carries on silently. */
        fun onCueError(cue: Cue, error: Exception) {}
    }

    private var engine: TimerEngine? = null
    private var expectedTickAtMs = 0L

    var lastState: TimerState = TimerState()
        private set

    val status: TimerStatus get() = engine?.status ?: TimerStatus.NOT_STARTED
    val isActive: Boolean get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED

    /** Starts a workout. Ignored (false) while one is running or paused. */
    fun start(config: TimerConfig): Boolean {
        if (isActive) return false
        scheduler.cancel()
        val newEngine = TimerEngine(config.sanitized())
        engine = newEngine
        cues.prepare(newEngine.speechNeeded())
        publish(newEngine.start(clock()))
        scheduleNextTick()
        return true
    }

    fun pause() {
        val e = engine ?: return
        if (e.status != TimerStatus.RUNNING) return
        scheduler.cancel()
        cues.stopSpeaking()
        publish(e.pause(clock()))
    }

    fun resume() {
        val e = engine ?: return
        if (e.status != TimerStatus.PAUSED) return
        publish(e.resume(clock()))
        scheduleNextTick()
    }

    fun skip() {
        val e = engine ?: return
        publish(e.skip(clock()))
        // The pending tick was planned for the old segment's second boundary.
        scheduleNextTick()
    }

    fun lap() {
        val e = engine ?: return
        publish(e.lap(clock()))
    }

    /** Abandons the workout without publishing anything. */
    fun stop() {
        scheduler.cancel()
        cues.stopSpeaking()
        engine = null
        lastState = TimerState()
    }

    /** One scheduled tick. Internal so tests can drive it directly. */
    internal fun onTick() {
        val e = engine ?: return
        val now = clock()
        val late = now - expectedTickAtMs
        if (late > LATE_TICK_MS) listener.onLateTick(late)
        publish(e.tick(now))
        scheduleNextTick()
    }

    private fun scheduleNextTick() {
        val e = engine ?: return
        if (e.status != TimerStatus.RUNNING) {
            scheduler.cancel()
            return
        }
        val now = clock()
        val delay = e.millisUntilNextTick(now)
        expectedTickAtMs = now + delay
        scheduler.scheduleAfter(delay) { onTick() }
    }

    private fun publish(result: EngineResult) {
        for (cue in result.cues) {
            try {
                cues.play(cue)
            } catch (e: Exception) {
                listener.onCueError(cue, e)
            }
        }
        lastState = result.state
        listener.onStateChanged(result.state)
        if (result.state.status == TimerStatus.FINISHED) {
            scheduler.cancel()
            listener.onFinished(result.state)
        }
    }

    private companion object {
        const val LATE_TICK_MS = 500L
    }
}
