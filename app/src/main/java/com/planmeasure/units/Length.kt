package com.planmeasure.units

import com.planmeasure.protocol.DistanceFrame
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Conversion between the canonical length stored in documents and the unit shown to the user.
 *
 * The canonical form is decimal millimeters with up to [CANONICAL_SCALE] fraction digits. That
 * scale represents every supported unit and every value the rangefinder produces without loss, so
 * changing the unit of a document only changes one field and never rewrites or rounds the stored
 * measurements (ADR 0009).
 */
object Length {

    /** Nanometer precision: finer than the device and than any manually entered value. */
    const val CANONICAL_SCALE = 6

    /** Canonical text for [millimeters], without trailing zeros. */
    fun canonical(millimeters: BigDecimal): String = millimeters
        .setScale(CANONICAL_SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

    /** Canonical millimeters of [value] entered in [unit]. */
    fun toMillimeters(value: BigDecimal, unit: LengthUnit): String =
        canonical(value.multiply(unit.millimeters))

    /**
     * [millimetersText] formatted in [unit] with the precision that unit is displayed with.
     *
     * Rounding happens only here; the stored value stays exact. Unknown text is returned as is so
     * that a damaged document still shows something instead of an empty label.
     */
    fun format(millimetersText: String, unit: LengthUnit): String {
        val millimeters = millimetersText.toBigDecimalOrNull() ?: return millimetersText
        return millimeters
            .divide(unit.millimeters, unit.displayDecimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}

/** Canonical millimeters of a decoded reading, or `null` when its unit is unknown. */
fun DistanceFrame.toCanonicalMillimeters(): String? {
    val unit = unit.toLengthUnit() ?: return null
    val value = normalizedText.toBigDecimalOrNull() ?: return null
    return Length.toMillimeters(value, unit)
}
