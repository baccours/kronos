package com.baccours.kronos.model

import kotlinx.serialization.Serializable

@Serializable
enum class TimerMode {
    TIMER, FOR_TIME
}

@Serializable
enum class TaskType {
    WORK, REST
}

@Serializable
data class Task(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: TaskType = TaskType.WORK,
    val durationSeconds: Int = 30
)

data class IndexedTask(
    val task: Task,
    val displayIndex: Int
)

@Serializable
data class TimerConfig(
    val mode: TimerMode = TimerMode.TIMER,
    val sets: Int = 1,
    val setRestSeconds: Int = 0,
    /** Per-set time cap in seconds (0 = disabled). */
    val timeCap: Int = 0,
    /** Total workout time cap in seconds (0 = disabled). */
    val totalTimeCap: Int = 0,
    val rounds: Int = 1,
    val tasks: List<Task> = listOf(Task(type = TaskType.WORK), Task(type = TaskType.REST)),
) {
    /** Task items numbered independently by type. Computed once per instance (not serialized). */
    val indexedTasks: List<IndexedTask> by lazy {
        var workCount = 0
        var restCount = 0
        tasks.map { task ->
            val index = if (task.type == TaskType.WORK) ++workCount else ++restCount
            IndexedTask(task, index)
        }
    }
    val activeTotalTimeCap: Int
        get() = if (mode == TimerMode.FOR_TIME && sets > 1) totalTimeCap.coerceAtLeast(0) else 0

    /** Planned total duration in seconds (excluding get-ready). */
    val totalDurationSeconds: Int
        get() {
            val tasksDuration = tasks.sumOf { it.durationSeconds.toLong() }
            val totalRounds = rounds.toLong() * sets
            val activeDuration = tasksDuration * totalRounds
            val setRestDuration = setRestSeconds.toLong() * (sets - 1)
            return (activeDuration + setRestDuration).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

    /**
     * Returns a copy with all values clamped within allowed ranges.
     */
    fun sanitized(): TimerConfig = copy(
        sets = sets.coerceIn(1, MAX_SETS),
        rounds = rounds.coerceIn(1, MAX_ROUNDS),
        setRestSeconds = setRestSeconds.coerceIn(0, MAX_DURATION_SECONDS),
        timeCap = timeCap.coerceIn(0, MAX_DURATION_SECONDS),
        totalTimeCap = totalTimeCap.coerceIn(0, MAX_DURATION_SECONDS),
        tasks = tasks.take(MAX_TASKS).map { task ->
            if (task.durationSeconds in 0..MAX_DURATION_SECONDS) {
                task
            } else {
                task.copy(durationSeconds = task.durationSeconds.coerceIn(0, MAX_DURATION_SECONDS))
            }
        }
    )

    companion object {
        const val MAX_SETS = 99
        const val MAX_ROUNDS = 99

        /** Maximum allowed tasks to prevent integer overflow in total duration. */
        const val MAX_TASKS = 36

        /** Maximum input duration in seconds (99:59). */
        const val MAX_DURATION_SECONDS = 99 * 60 + 59
    }
}
