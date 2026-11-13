package com.baccours.kronos.format

import java.util.Locale

/** Upper-cases for display using the user's locale. Strings are stored in natural case for translators. */
fun String.caps(): String = uppercase(Locale.getDefault())
