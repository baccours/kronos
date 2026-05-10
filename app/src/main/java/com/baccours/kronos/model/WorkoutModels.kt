package com.baccours.kronos.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class WorkoutMode : Parcelable {
    TIMER, FOR_TIME
}

@Parcelize
enum class WorkoutItemType : Parcelable {
    WORK, REST
}

@Parcelize
data class WorkoutItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val type: WorkoutItemType = WorkoutItemType.WORK,
    val durationSeconds: Int = 30
) : Parcelable

@Parcelize
data class WorkoutConfig(
    val mode: WorkoutMode = WorkoutMode.TIMER,
    val sets: Int = 1,
    val roundsPerSet: Int = 1,
    val items: List<WorkoutItem> = listOf(WorkoutItem(type = WorkoutItemType.WORK), WorkoutItem(type = WorkoutItemType.REST)),
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
