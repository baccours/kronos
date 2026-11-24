package com.baccours.kronos.format

/** Formats seconds as MM:SS (or HH:MM:SS). Locale-independent so digits are always 0-9. */
fun Int.formatDuration(): String {
    val total = coerceAtLeast(0)
    val h = total / 3600
    return buildString(8) { // No Formatter: this runs several times per second while a workout is shown.
        if (h > 0) append2(h).append(':')
        append2((total % 3600) / 60).append(':')
        append2(total % 60)
    }
}

private fun StringBuilder.append2(v: Int): StringBuilder {
    if (v < 10) append('0')
    return append(v)
}
