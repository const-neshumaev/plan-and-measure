package com.planmeasure.units

import com.planmeasure.protocol.DistanceUnit
import java.math.BigDecimal
import kotlinx.serialization.Serializable

/**
 * Unit a document uses to show its lengths. Serialized by enum name.
 *
 * Values are stored canonically in millimeters (see [Length]); the unit only decides how a value
 * is formatted and how many decimal places manual input accepts. That is why centimeters and
 * millimeters, which the rangefinder never sends, are as valid as the device units.
 */
@Serializable
enum class LengthUnit(
    val millimeters: BigDecimal,
    val displayDecimals: Int,
) {
    METERS(BigDecimal(1000), 3),
    CENTIMETERS(BigDecimal(10), 1),
    MILLIMETERS(BigDecimal.ONE, 1),
    FEET(BigDecimal("304.8"), 4),
    INCHES(BigDecimal("25.4"), 4),
}

/** Maps a protocol unit to a storage unit; `UNKNOWN` frames cannot be stored. */
fun DistanceUnit.toLengthUnit(): LengthUnit? = when (this) {
    DistanceUnit.METERS -> LengthUnit.METERS
    DistanceUnit.FEET -> LengthUnit.FEET
    DistanceUnit.INCHES -> LengthUnit.INCHES
    DistanceUnit.MILLIMETERS -> LengthUnit.MILLIMETERS
    DistanceUnit.UNKNOWN -> null
}
