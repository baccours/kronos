package com.baccours.kronos.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
enum class TimerMode : Parcelable {
    TIMER, FOR_TIME
}

@Serializable
@Parcelize
enum class TaskType : Parcelable {
    WORK, REST
}

@Serializable
@Parcelize
data class Task(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: TaskType = TaskType.WORK,
    val durationSeconds: Int = 30
) : Parcelable

@Serializable
@Parcelize
data class IndexedTask(
    val task: Task,
    val displayIndex: Int
) : Parcelable

@Serializable
@Parcelize
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
) : Parcelable {
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

    val totalDurationSeconds: Int
        get() {
            val tasksDuration = tasks.sumOf { it.durationSeconds }
            val totalRounds = rounds * sets
            val activeDuration = tasksDuration * totalRounds
            val setRestDuration = setRestSeconds * (sets - 1)
            return activeDuration + setRestDuration
        }
}
