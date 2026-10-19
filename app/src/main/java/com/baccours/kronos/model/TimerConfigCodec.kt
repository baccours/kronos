package com.baccours.kronos.model

import kotlinx.serialization.json.Json

/**
 * The one place that turns a [TimerConfig] into text and back. Used for the saved setup
 * (DataStore) and for handing the workout to the service. Decoded configs are sanitized.
 */
object TimerConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(config: TimerConfig): String = json.encodeToString(TimerConfig.serializer(), config)

    /** Null when [text] is missing or not a valid config (SerializationException is an IllegalArgumentException). */
    fun decode(text: String?): TimerConfig? {
        if (text.isNullOrEmpty()) return null
        return try {
            json.decodeFromString(TimerConfig.serializer(), text).sanitized()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
