package com.planmeasure.projects

import kotlinx.coroutines.flow.StateFlow

/**
 * Storage of saved projects.
 *
 * Like plans, the repository keeps complete documents in memory: the tree and the add-object
 * picker resolve names of referenced documents, and projects are small JSON files (ADR 0015).
 */
interface ProjectRepository {

    /** All projects, newest update first. */
    val projects: StateFlow<List<ProjectDocument>>

    /** Rereads the storage directory and rebuilds [projects]. */
    suspend fun reload()

    /** Returns the full document, or `null` if it is missing or unreadable. */
    suspend fun load(id: String): ProjectDocument?

    suspend fun create(name: String): ProjectDocument

    /** Writes the document and refreshes the in-memory list. */
    suspend fun update(project: ProjectDocument)

    /** Writes several changed documents under one lock and publishes them together. */
    suspend fun updateAll(projects: List<ProjectDocument>)

    suspend fun rename(id: String, name: String): ProjectDocument?

    suspend fun delete(id: String)
}
