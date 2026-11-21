package com.baccours.kronos.model

import kotlinx.serialization.json.Json

/**
 * JSON serializer/deserializer for [TimerConfig].
 */
object TimerConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true // unknown enum value -> default, not a lost setup
    }

    fun encode(config: TimerConfig): String = json.encodeToString(TimerConfig.serializer(), config)

    /** Decodes and sanitizes JSON string, returning null if invalid. */
    fun decode(text: String?): TimerConfig? {
        if (text.isNullOrEmpty()) return null
        return try {
            json.decodeFromString(TimerConfig.serializer(), text).sanitized()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
