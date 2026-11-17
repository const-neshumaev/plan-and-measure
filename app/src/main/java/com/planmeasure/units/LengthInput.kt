package com.planmeasure.units

import java.math.BigDecimal

/**
 * Validation and canonicalization of a manually entered length.
 *
 * Comma and dot are both accepted as the decimal separator because the keyboard layout depends on
 * the user's locale. The number of decimal places is limited by the unit of the document: a value
 * that would not survive the round trip through formatting is rejected instead of being rounded
 * silently.
 */
object LengthInput {

    private val pattern = Regex("^\\d{1,6}([.,]\\d{1,4})?$")

    /** Returns the canonical millimeters of [raw], or `null` when it is not a usable value. */
    fun normalize(raw: String, unit: LengthUnit): String? {
        val trimmed = raw.trim()
        if (!pattern.matches(trimmed)) return null
        val value = runCatching { BigDecimal(trimmed.replace(',', '.')) }.getOrNull() ?: return null
        if (value.signum() <= 0) return null
        if (value.stripTrailingZeros().scale() > unit.displayDecimals) return null
        return Length.toMillimeters(value, unit)
    }
}
