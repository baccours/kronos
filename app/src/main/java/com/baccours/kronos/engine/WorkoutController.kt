package com.baccours.kronos.engine

import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerState
import com.baccours.kronos.model.TimerStatus

/** Audio cue player interface. */
interface CuePlayer {
    fun play(cue: Cue)

    /** Preloads audio assets required for upcoming announcements. */
    fun prepare(speeches: List<Speech>)

    fun stopSpeaking()

    fun release()
}

/** Single-task delayed scheduler. Rescheduling replaces pending tasks. */
interface TickScheduler {
    fun scheduleAfter(delayMs: Long, task: () -> Unit)

    fun cancel()
}

/**
 * Controller managing [TimerEngine] lifecycle, tick scheduling, audio cues, and state updates.
 * Pure Kotlin; main-thread only.
 */
class WorkoutController(
    private val cues: CuePlayer,
    private val scheduler: TickScheduler,
    /** Monotonic clock provider (milliseconds). */
    private val clock: () -> Long,
    private val listener: Listener
) {

    interface Listener {
        /** Callback for workout state changes. */
        fun onStateChanged(state: TimerState)

        /** Callback when workout completes. */
        fun onFinished(state: TimerState)

        /** Callback when a scheduled tick is delayed. */
        fun onLateTick(lateMs: Long) {}

        /** Callback on cue playback errors. */
        fun onCueError(cue: Cue, error: Exception) {}
    }

    private var engine: TimerEngine? = null
    private var expectedTickAtMs = 0L

    var lastState: TimerState = TimerState()
        private set

    val status: TimerStatus get() = engine?.status ?: TimerStatus.NOT_STARTED
    val isActive: Boolean get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED

    /** Starts workout with [config]. Returns false if already active. */
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
        // Ignored once finished: a late tap must not re-trigger onFinished.
        val e = engine?.takeIf { isActive } ?: return
        publish(e.skip(clock()))
        // Reschedule tick for new segment boundary.
        scheduleNextTick()
    }

    fun lap() {
        val e = engine?.takeIf { isActive } ?: return
        publish(e.lap(clock()))
    }

    /** Cancels active workout without emitting completion. */
    fun stop() {
        scheduler.cancel()
        cues.stopSpeaking()
        engine = null
        lastState = TimerState()
    }

    /** Processes a single scheduled tick. */
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
