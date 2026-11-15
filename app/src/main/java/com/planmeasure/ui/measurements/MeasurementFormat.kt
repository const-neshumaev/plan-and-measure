package com.planmeasure.ui.measurements

import androidx.annotation.StringRes
import com.planmeasure.R
import com.planmeasure.measurements.LengthUnit
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@StringRes
fun LengthUnit.labelRes(): Int = when (this) {
    LengthUnit.METERS -> R.string.unit_meters
    LengthUnit.CENTIMETERS -> R.string.unit_centimeters
    LengthUnit.MILLIMETERS -> R.string.unit_millimeters
    LengthUnit.FEET -> R.string.unit_feet
    LengthUnit.INCHES -> R.string.unit_inches
}

/** Time for today's readings, full date and time for older ones. */
fun formatMeasurementTime(
    epochMs: Long,
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
): String = if (isSameDay(epochMs, now)) {
    DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(epochMs))
} else {
    dateTimeFormatter(locale).format(Date(epochMs))
}

private fun dateTimeFormatter(locale: Locale): DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)

private fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
