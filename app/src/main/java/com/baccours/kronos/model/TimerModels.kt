package com.baccours.kronos.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class TimerMode : Parcelable {
    TIMER, FOR_TIME
}

@Parcelize
enum class TaskType : Parcelable {
    WORK, REST
}

@Parcelize
data class Task(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: TaskType = TaskType.WORK,
    val durationSeconds: Int = 30
) : Parcelable

@Parcelize
data class IndexedTask(
    val task: Task,
    val displayIndex: Int
) : Parcelable

@Parcelize
data class TimerConfig(
    val mode: TimerMode = TimerMode.TIMER,
    val sets: Int = 1,
    val setRestSeconds: Int = 0,
    val timeCap: Int = 0,
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

    val totalDurationSeconds: Int
        get() {
            val tasksDuration = tasks.sumOf { it.durationSeconds }
            val totalRounds = rounds * sets
            val activeDuration = tasksDuration * totalRounds
            val setRestDuration = setRestSeconds * (sets - 1)
            return activeDuration + setRestDuration
        }
}
