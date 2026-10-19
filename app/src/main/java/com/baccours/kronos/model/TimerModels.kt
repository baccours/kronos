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
    /** For Time: cap per set in seconds, 0 = none. */
    val timeCap: Int = 0,
    /** For Time: cap on the whole workout (all sets and rests) in seconds, 0 = none. */
    val totalTimeCap: Int = 0,
    val rounds: Int = 1,
    val tasks: List<Task> = listOf(Task(type = TaskType.WORK), Task(type = TaskType.REST)),
) {
    /** Tasks with independent numbering: WORK 1, WORK 2, ... and REST 1, REST 2, ... */
    val indexedTasks: List<IndexedTask>
        get() {
            var workCount = 0
            var restCount = 0
            return tasks.map { task ->
                val index = if (task.type == TaskType.WORK) ++workCount else ++restCount
                IndexedTask(task, index)
            }
        }
    val activeTotalTimeCap: Int
        get() = if (mode == TimerMode.FOR_TIME && sets > 1) totalTimeCap.coerceAtLeast(0) else 0

    /** Planned length of the whole workout (without "get ready"). Never overflows an Int. */
    val totalDurationSeconds: Int
        get() {
            val tasksDuration = tasks.sumOf { it.durationSeconds.toLong() }
            val totalRounds = rounds.toLong() * sets
            val activeDuration = tasksDuration * totalRounds
            val setRestDuration = setRestSeconds.toLong() * (sets - 1)
            return (activeDuration + setRestDuration).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

    /**
     * A copy with every value forced into the range the UI allows. Configs come from storage
     * (which can be corrupted or restored from a backup), so they are cleaned before use.
     * With these limits [totalDurationSeconds] always fits in an Int.
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

        /**
         * The most tasks for which the longest possible workout still fits in an Int (seconds):
         * 36 x 5999 x 99 x 99 + 5999 x 98 = 2,117,251,066 < Int.MAX_VALUE (2,147,483,647).
         * A 37th task would overflow (2,175,459,363 + ...), see TimerConfigTest.
         */
        const val MAX_TASKS = 36

        /** 99:59, the largest value the time input can produce. */
        const val MAX_DURATION_SECONDS = 99 * 60 + 59
    }
}
