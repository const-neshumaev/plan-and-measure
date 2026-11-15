package com.planmeasure.measurements

import com.planmeasure.protocol.DistanceUnit
import kotlinx.serialization.Serializable

/** Length unit a measurement was entered or received in. Serialized by enum name. */
@Serializable
enum class LengthUnit {
    METERS,
    CENTIMETERS,
    MILLIMETERS,
    FEET,
    INCHES,
}

/** Maps a protocol unit to a storage unit; `UNKNOWN` frames cannot be stored. */
fun DistanceUnit.toLengthUnit(): LengthUnit? = when (this) {
    DistanceUnit.METERS -> LengthUnit.METERS
    DistanceUnit.FEET -> LengthUnit.FEET
    DistanceUnit.INCHES -> LengthUnit.INCHES
    DistanceUnit.MILLIMETERS -> LengthUnit.MILLIMETERS
    DistanceUnit.UNKNOWN -> null
}
