package com.baccours.kronos.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User preferences that are independent of the workout setup. */
data class AppSettings(
    /** Beeps, start buzzer and finish jingle. */
    val beeps: Boolean = true,
    /** Spoken announcements. */
    val voice: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM
)
