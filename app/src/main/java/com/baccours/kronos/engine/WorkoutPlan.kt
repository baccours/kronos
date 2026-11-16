package com.baccours.kronos.engine

import com.baccours.kronos.model.Phase
import com.baccours.kronos.model.TaskType
import com.baccours.kronos.model.TimerConfig
import com.baccours.kronos.model.TimerMode
import com.baccours.kronos.model.numbered

internal data class Segment(
    val phase: Phase,
    val set: Int,
    val round: Int,
    val workIndex: Int,
    /** Segment duration in seconds (0 = open-ended). */
    val durationSeconds: Int
)

/**
 * The workout's segments, computed on demand from the config: a huge workout
 * (99 sets x 99 rounds x 36 tasks) then costs neither setup time nor memory.
 * Index 0 is always the READY segment, so the plan is never empty.
 */
internal class WorkoutPlan(private val config: TimerConfig) : AbstractList<Segment>() {
    private val active = config.tasks.numbered().filter { it.task.durationSeconds > 0 }
    private val isTimer = config.mode == TimerMode.TIMER
    private val perSet = if (isTimer) config.rounds * active.size else 1
    private val hasSetRest = config.setRestSeconds > 0

    /** Segments per set, including the rest after it. */
    val stride = perSet + if (hasSetRest) 1 else 0

    override val size = 1 + stride * config.sets - if (hasSetRest) 1 else 0

    override fun get(index: Int): Segment {
        if (index == 0) return Segment(Phase.READY, 1, 1, 0, READY_SECONDS)
        val set = (index - 1) / stride + 1
        val offset = (index - 1) % stride
        if (offset == perSet) {
            return Segment(Phase.SET_REST, set, if (isTimer) config.rounds else 1, 0, config.setRestSeconds)
        }
        if (!isTimer) return Segment(Phase.WORK, set, 1, 0, config.timeCap.coerceAtLeast(0))
        val (task, displayIndex) = active[offset % active.size]
        val isWork = task.type == TaskType.WORK
        return Segment(
            phase = if (isWork) Phase.WORK else Phase.REST,
            set = set,
            round = offset / active.size + 1,
            workIndex = if (isWork) displayIndex else 0,
            durationSeconds = task.durationSeconds
        )
    }

    companion object {
        const val READY_SECONDS = 10
    }
}
