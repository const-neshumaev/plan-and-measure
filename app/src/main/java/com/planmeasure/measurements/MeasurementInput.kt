package com.planmeasure.measurements

import java.math.BigDecimal

/**
 * Validation and canonicalization of a manually entered value.
 *
 * Comma and dot are both accepted as the decimal separator because the keyboard layout depends
 * on the user's locale. The stored form always uses a dot and no leading zeros.
 */
object MeasurementInput {

    private val pattern = Regex("^\\d{1,6}([.,]\\d{1,4})?$")

    /** Returns the canonical decimal text, or `null` when [raw] is not a positive number. */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (!pattern.matches(trimmed)) return null
        val value = runCatching { BigDecimal(trimmed.replace(',', '.')) }.getOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.toPlainString()
    }
}
