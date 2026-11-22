package com.planmeasure.projects

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for project documents.
 *
 * Unknown keys are ignored so that a newer app version can add fields without breaking older
 * ones reading the same file; defaults fill in fields written by older versions.
 */
object ProjectJson {

    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(project: ProjectDocument): String = format.encodeToString(project)

    fun decode(text: String): ProjectDocument = format.decodeFromString(text)
}
