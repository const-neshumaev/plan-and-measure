package com.planmeasure.plans

import kotlinx.coroutines.flow.StateFlow

/**
 * Storage of saved plans.
 *
 * Unlike measurement lists the repository keeps complete documents in memory: the list screen
 * shows a small drawing preview, so summaries alone are not enough. Plans are small JSON files,
 * so the trade-off is acceptable; see ADR 0004.
 */
interface PlanRepository {

    /** All plans, newest update first. */
    val plans: StateFlow<List<PlanDocument>>

    /** Rereads the storage directory and rebuilds [plans]. */
    suspend fun reload()

    /** Returns the full document, or `null` if it is missing or unreadable. */
    suspend fun load(id: String): PlanDocument?

    suspend fun create(name: String): PlanDocument

    /** Writes the document and refreshes the in-memory list. */
    suspend fun update(plan: PlanDocument)

    suspend fun rename(id: String, name: String): PlanDocument?

    suspend fun delete(id: String)
}
