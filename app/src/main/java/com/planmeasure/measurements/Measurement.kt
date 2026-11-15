package com.planmeasure.measurements

import kotlinx.serialization.Serializable

/** Where a measurement came from; kept so the list can explain its own contents. */
@Serializable
enum class MeasurementSource {
    MANUAL,
    RANGFINDER,
}

/**
 * One saved distance.
 *
 * The value is stored as the decimal text the user or the device produced rather than as a
 * [Double]: it keeps the exact digits shown on screen and avoids binary rounding artefacts.
 */
@Serializable
data class Measurement(
    val id: String,
    val valueText: String,
    val unit: LengthUnit,
    val comment: String = "",
    val createdAt: Long,
    val source: MeasurementSource,
)
