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
    /** Identifies the row in the setup editor, which assigns unique ids; the engine ignores it. */
    val id: String = "",
    val type: TaskType = TaskType.WORK,
    val durationSeconds: Int = 30
)

data class IndexedTask(
    val task: Task,
    val displayIndex: Int
)

/** Numbers tasks independently per type (WORK 1, REST 1, WORK 2...). */
fun List<Task>.numbered(): List<IndexedTask> {
    var work = 0
    var rest = 0
    return map { IndexedTask(it, if (it.type == TaskType.WORK) ++work else ++rest) }
}

/** Gives blank or repeated ids a fresh one: the setup editor identifies rows by id. */
private fun List<Task>.withUniqueIds(): List<Task> {
    val seen = HashSet<String>()
    return mapIndexed { i, task ->
        if (task.id.isNotEmpty() && seen.add(task.id)) return@mapIndexed task
        var n = i
        while (!seen.add("task-$n")) n++
        task.copy(id = "task-$n")
    }
}

/**
 * One flat config for both modes: fields a mode does not use are ignored by the engine but kept,
 * so the setup screen preserves them when the user switches mode.
 */
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
    val tasks: List<Task> = listOf(Task("work", TaskType.WORK), Task("rest", TaskType.REST)),
) {
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
     * Returns a copy with all values clamped within allowed ranges and unique task ids.
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
        }.withUniqueIds()
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
