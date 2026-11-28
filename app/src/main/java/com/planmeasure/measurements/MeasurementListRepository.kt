package com.planmeasure.measurements

import com.planmeasure.units.LengthUnit
import kotlinx.coroutines.flow.StateFlow

/**
 * Storage of named measurement lists.
 *
 * The interface exists so that the JSON implementation can be replaced (a database, an export
 * format) without touching the ViewModel or Compose code.
 */
interface MeasurementListRepository {

    /** Summaries of all saved lists, newest update first. */
    val lists: StateFlow<List<MeasurementListSummary>>

    /** Rereads the storage directory and rebuilds [lists]. */
    suspend fun reload()

    /** Returns the full document, or `null` if it is missing or unreadable. */
    suspend fun load(id: String): MeasurementList?

    /** Whether a document or its backup exists for [id], even if it cannot be decoded. */
    fun hasDocument(id: String): Boolean

    suspend fun create(name: String, unit: LengthUnit = LengthUnit.METERS): MeasurementList

    /** Writes the document and refreshes its summary. */
    suspend fun update(list: MeasurementList)

    suspend fun rename(id: String, name: String): MeasurementList?

    suspend fun delete(id: String)
}
