package com.planmeasure.protocol

import java.math.BigDecimal

enum class DistanceUnit {
    METERS,
    FEET,
    INCHES,
    MILLIMETERS,
    UNKNOWN,
}

/**
 * One frame received from characteristic 0xCBB1.
 *
 * The device mirrors what it shows on its display: an ASCII string padded with spaces
 * to 16 bytes, for example `D1.763m         ` or `D72.60ft        `.
 *
 * Only completed measurements are transmitted. Area, volume and Pythagoras results are
 * computed on the device and never sent; the app receives the individual distances.
 */
data class DistanceFrame(
    val raw: String,
    val prefix: Char,
    val value: Double,
    val valueText: String,
    val unit: DistanceUnit,
    val unitText: String,
) {

    /**
     * Value in the unit shown on the device display.
     *
     * Feet frames carry the decimal point one position to the right: `D67.61ft`
     * corresponds to `6.761 ft`. Meters are transmitted without this shift.
     */
    val normalizedValue: Double
        get() = when (unit) {
            DistanceUnit.FEET -> value / 10.0
            else -> value
        }

    /**
     * Decimal text with the feet shift already corrected.
     *
     * The device sends feet with the point one digit to the right (`D72.60ft` = `7.26 ft`).
     * Storage keeps this normalized text so that the saved value matches the display.
     */
    val normalizedText: String
        get() = when (unit) {
            DistanceUnit.FEET -> BigDecimal(valueText)
                .movePointLeft(1)
                .stripTrailingZeros()
                .toPlainString()

            else -> valueText
        }

    /** Distance in millimeters, or `null` when the unit is unknown. */
    val valueMm: Double?
        get() = when (unit) {
            DistanceUnit.METERS -> normalizedValue * 1000.0
            DistanceUnit.FEET -> normalizedValue * 304.8
            DistanceUnit.INCHES -> normalizedValue * 25.4
            DistanceUnit.MILLIMETERS -> normalizedValue
            DistanceUnit.UNKNOWN -> null
        }
}

object FrameCodec {

    const val FRAME_LENGTH = 16

    private val pattern = Regex("^([A-Za-z])(-?\\d+(?:\\.\\d+)?)([A-Za-z]*)$")

    /**
     * Decodes a notification payload. Returns `null` for placeholders such as `D---m`
     * and for anything that does not look like a measurement.
     */
    fun decode(bytes: ByteArray): DistanceFrame? {
        if (bytes.isEmpty()) return null
        val raw = String(bytes, Charsets.US_ASCII)
        val trimmed = raw.trimEnd { it == ' ' || it == '\u0000' }
        val match = pattern.matchEntire(trimmed) ?: return null
        val prefix = match.groupValues[1][0]
        val valueText = match.groupValues[2]
        val unitText = match.groupValues[3]
        val value = valueText.toDoubleOrNull() ?: return null
        return DistanceFrame(
            raw = raw,
            prefix = prefix,
            value = value,
            valueText = valueText,
            unit = unitOf(unitText),
            unitText = unitText,
        )
    }

    fun unitOf(text: String): DistanceUnit = when (text.lowercase()) {
        "m" -> DistanceUnit.METERS
        "ft" -> DistanceUnit.FEET
        "in" -> DistanceUnit.INCHES
        "mm" -> DistanceUnit.MILLIMETERS
        else -> DistanceUnit.UNKNOWN
    }
}
