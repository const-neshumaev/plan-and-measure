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
 * The value is canonical decimal millimeters (see `com.planmeasure.units.Length`), not a [Double]:
 * it keeps the exact digits of the reading, and the unit of the list decides how it is shown.
 */
@Serializable
data class Measurement(
    val id: String,
    val valueMm: String,
    val comment: String = "",
    val createdAt: Long,
    val source: MeasurementSource,
)
