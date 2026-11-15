package com.planmeasure.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Timestamp of a saved document on the overview screens. */
fun formatUpdatedAt(epochMs: Long, locale: Locale = Locale.getDefault()): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        .format(Date(epochMs))

/** Default name for a new document, so several unsaved-looking ones stay distinguishable. */
fun defaultNameTimestamp(locale: Locale): String =
    SimpleDateFormat("d MMM yyyy HH:mm", locale).format(Date())
