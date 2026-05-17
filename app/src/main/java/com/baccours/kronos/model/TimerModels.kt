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
    val name: String = "",
    val type: TaskType = TaskType.WORK,
    val durationSeconds: Int = 30
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
    val totalDurationSeconds: Int
        get() {
            val tasksDuration = tasks.sumOf { it.durationSeconds }
            val totalRounds = rounds * sets
            val activeDuration = tasksDuration * totalRounds
            val setRestDuration = setRestSeconds * (sets - 1)
            return activeDuration + setRestDuration
        }
}
