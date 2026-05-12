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
    val roundsPerSet: Int = 1,
    val items: List<Task> = listOf(Task(type = TaskType.WORK), Task(type = TaskType.REST)),
    val restBetweenSetsSeconds: Int = 60
) : Parcelable {
    val totalDurationSeconds: Int
        get() {
            val itemsDuration = items.sumOf { it.durationSeconds }
            val totalRounds = roundsPerSet * sets
            val activeDuration = itemsDuration * totalRounds
            val setRestDuration = restBetweenSetsSeconds * (sets - 1)
            return activeDuration + setRestDuration
        }
}
