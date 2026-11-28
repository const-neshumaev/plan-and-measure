package com.planmeasure.units

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Conversion of an area stored canonically in square millimeters to the unit shown to the user.
 *
 * The plan's [LengthUnit] decides the area unit: meters become square meters, centimeters square
 * centimeters and so on. As with lengths, only the presentation is converted; computations stay in
 * square millimeters.
 */
object Area {

    /** [squareMillimeters] formatted in the square of [unit] with the unit's display precision. */
    fun format(squareMillimeters: Double, unit: LengthUnit): String {
        if (!squareMillimeters.isFinite()) return ""
        val millimetersPerUnit = unit.millimeters
        val squareMillimetersPerUnit = millimetersPerUnit.multiply(millimetersPerUnit)
        return BigDecimal.valueOf(squareMillimeters)
            .divide(squareMillimetersPerUnit, unit.displayDecimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
