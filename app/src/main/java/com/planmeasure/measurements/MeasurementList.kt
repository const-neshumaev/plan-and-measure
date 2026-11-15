package com.planmeasure.measurements

import kotlinx.serialization.Serializable

/** Current version of the on-disk document. Bump when the shape changes. */
const val MEASUREMENT_LIST_SCHEMA_VERSION = 1

/**
 * One named measurement list, stored as `measurements/<id>.json`.
 *
 * The list name lives in the document, so renaming never touches the file: file names stay
 * ASCII-safe and free of user input.
 */
@Serializable
data class MeasurementList(
    val schemaVersion: Int = MEASUREMENT_LIST_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val measurements: List<Measurement> = emptyList(),
) {
    fun summary(): MeasurementListSummary = MeasurementListSummary(
        id = id,
        name = name,
        measurementCount = measurements.size,
        updatedAt = updatedAt,
    )
}

/** Lightweight row for the saved-lists screen; never persisted on its own. */
data class MeasurementListSummary(
    val id: String,
    val name: String,
    val measurementCount: Int,
    val updatedAt: Long,
)
