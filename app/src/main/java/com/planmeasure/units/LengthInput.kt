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

    private const val MAX_INTEGER_DIGITS = 6

    private val pattern = Regex("^\\d{1,$MAX_INTEGER_DIGITS}([.,]\\d{0,4})?$")

    /**
     * Removes characters that cannot be part of a length in [unit].
     *
     * The input field is masked instead of showing an error: letters and extra separators never
     * appear, the integer part is capped at [MAX_INTEGER_DIGITS] digits, and the fraction is capped
     * at the precision the unit displays. A separator typed first gets a leading zero so that `.5`
     * does not turn into `5`.
     */
    fun filter(raw: String, unit: LengthUnit): String {
        val result = StringBuilder()
        var integerDigits = 0
        var fractionDigits = 0
        var hasSeparator = false
        for (char in raw) {
            when {
                char in '0'..'9' && !hasSeparator && integerDigits < MAX_INTEGER_DIGITS -> {
                    result.append(char)
                    integerDigits++
                }

                char in '0'..'9' && hasSeparator && fractionDigits < unit.displayDecimals -> {
                    result.append(char)
                    fractionDigits++
                }

                (char == '.' || char == ',') && !hasSeparator -> {
                    if (integerDigits == 0) {
                        result.append('0')
                        integerDigits = 1
                    }
                    result.append(char)
                    hasSeparator = true
                }
            }
        }
        return result.toString()
    }

    /**
     * Returns the canonical millimeters of [raw], or `null` when it is not a usable value.
     *
     * A trailing separator is read as a whole number: `10.` and `10,` mean 10.
     */
    fun normalize(raw: String, unit: LengthUnit): String? {
        val trimmed = raw.trim()
        if (!pattern.matches(trimmed)) return null
        val number = trimmed.trimEnd('.', ',').replace(',', '.')
        val value = runCatching { BigDecimal(number) }.getOrNull() ?: return null
        if (value.signum() <= 0) return null
        if (value.stripTrailingZeros().scale() > unit.displayDecimals) return null
        return Length.toMillimeters(value, unit)
    }
}
