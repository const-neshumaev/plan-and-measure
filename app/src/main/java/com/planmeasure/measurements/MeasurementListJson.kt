package com.planmeasure.measurements

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for measurement documents.
 *
 * Unknown keys are ignored so that a newer app version can add fields without breaking older
 * ones reading the same file; defaults fill in fields written by older versions.
 */
object MeasurementListJson {

    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(list: MeasurementList): String = format.encodeToString(list)

    fun decode(text: String): MeasurementList = format.decodeFromString(text)
}
