package com.baccours.kronos.model

import java.util.Locale

/** Formats seconds as MM:SS (or HH:MM:SS). Locale-independent so digits are always 0-9. */
fun Int.formatDuration(): String {
    val total = coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}
