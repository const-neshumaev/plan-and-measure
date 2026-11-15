package com.planmeasure.plans

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for plan documents.
 *
 * Unknown keys are ignored so that a newer app version can add fields without breaking older
 * ones reading the same file; defaults fill in fields written by older versions.
 */
object PlanJson {

    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(plan: PlanDocument): String = format.encodeToString(plan)

    fun decode(text: String): PlanDocument = format.decodeFromString(text)
}
