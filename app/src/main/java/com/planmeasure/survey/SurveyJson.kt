package com.planmeasure.survey

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for survey documents.
 *
 * Unknown keys are ignored so that a newer app version can add fields without breaking older ones
 * reading the same file; defaults fill in fields written by older versions.
 */
object SurveyJson {

    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(survey: SurveyDocument): String = format.encodeToString(survey)

    fun decode(text: String): SurveyDocument = format.decodeFromString(text)
}
