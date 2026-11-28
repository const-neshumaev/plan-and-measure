package com.planmeasure.survey

import com.planmeasure.units.LengthUnit
import kotlinx.coroutines.flow.StateFlow

/**
 * Storage of saved photo sketches.
 *
 * Like plans, complete documents are kept in memory: the list screen shows a photo preview and the
 * editor needs the whole sketch, so summaries alone are not enough. A survey is small JSON plus one
 * photo file, see ADR 0022.
 */
interface SurveyRepository {

    /** All surveys, newest update first. */
    val surveys: StateFlow<List<SurveyDocument>>

    /** Rereads the storage directory and rebuilds [surveys]. */
    suspend fun reload()

    /** Returns the full document, or `null` if it is missing or unreadable. */
    suspend fun load(id: String): SurveyDocument?

    /** Whether a document or its backup exists for [id], even if it cannot be decoded. */
    fun hasDocument(id: String): Boolean

    suspend fun create(name: String, unit: LengthUnit = LengthUnit.METERS): SurveyDocument

    /** Writes the document and refreshes the in-memory list. */
    suspend fun update(survey: SurveyDocument)

    suspend fun rename(id: String, name: String): SurveyDocument?

    /** Deletes the document together with its photo. */
    suspend fun delete(id: String)
}
