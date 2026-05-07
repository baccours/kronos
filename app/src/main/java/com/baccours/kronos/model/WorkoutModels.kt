package com.baccours.kronos.model

import java.io.Serializable

enum class WorkoutMode : Serializable {
    TIMER, FOR_TIME
}

enum class WorkoutItemType : Serializable {
    WORK, REST
}

data class WorkoutItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val type: WorkoutItemType = WorkoutItemType.WORK,
    val durationSeconds: Int = 30
) : Serializable

data class WorkoutConfig(
    val mode: WorkoutMode = WorkoutMode.TIMER,
    val sets: Int = 1,
    val roundsPerSet: Int = 1,
    val items: List<WorkoutItem> = listOf(WorkoutItem(type = WorkoutItemType.WORK), WorkoutItem(type = WorkoutItemType.REST)),
    val restBetweenSetsSeconds: Int = 60
) : Serializable {
    val totalDurationSeconds: Int
        get() {
            val itemsDuration = items.sumOf { it.durationSeconds }
            val totalRounds = roundsPerSet * sets
            val activeDuration = itemsDuration * totalRounds
            val setRestDuration = restBetweenSetsSeconds * (sets - 1)
            return activeDuration + setRestDuration
        }
}
