package com.planmeasure.projects

import com.planmeasure.storage.JsonDocumentStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stores every project as one JSON document in [directory], with the same atomic write and backup
 * rules as measurement lists, plans and surveys. A single [Mutex] serializes file access, so moving an
 * object between two projects cannot interleave with another edit.
 */
class JsonProjectRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ProjectRepository {

    private val store = JsonDocumentStore(directory)
    private val mutex = Mutex()
    private val documents = LinkedHashMap<String, ProjectDocument>()

    private val _projects = MutableStateFlow<List<ProjectDocument>>(emptyList())
    override val projects: StateFlow<List<ProjectDocument>> = _projects.asStateFlow()

    override suspend fun reload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.prepare()
            documents.clear()
            store.documents().forEach { file ->
                val document = readDocument(file) ?: return@forEach
                documents[document.id] = document
            }
            publishLocked()
        }
    }

    override suspend fun load(id: String): ProjectDocument? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(id)) return@withLock null
            readDocument(store.fileFor(id))
        }
    }

    override suspend fun create(name: String): ProjectDocument =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Project name must not be blank" }
                val now = clock()
                val document = ProjectDocument(
                    id = idGenerator(),
                    name = trimmed,
                    createdAt = now,
                    updatedAt = now,
                )
                writeDocument(document)
                documents[document.id] = document
                publishLocked()
                document
            }
        }

    override suspend fun update(project: ProjectDocument) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(project.id)) return@withLock
            writeDocument(project)
            documents[project.id] = project
            publishLocked()
        }
    }

    override suspend fun updateAll(projects: List<ProjectDocument>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var changed = false
                projects.forEach { project ->
                    if (!isValidId(project.id)) return@forEach
                    writeDocument(project)
                    documents[project.id] = project
                    changed = true
                }
                if (changed) publishLocked()
            }
        }

    override suspend fun rename(id: String, name: String): ProjectDocument? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val trimmed = name.trim()
                if (trimmed.isEmpty() || !isValidId(id)) return@withLock null
                val current = readDocument(store.fileFor(id)) ?: return@withLock null
                val updated = current.copy(name = trimmed, updatedAt = clock())
                writeDocument(updated)
                documents[updated.id] = updated
                publishLocked()
                updated
            }
        }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(id)) return@withLock
            store.delete(id)
            documents.remove(id)
            publishLocked()
        }
    }

    private fun isValidId(id: String): Boolean = runCatching { UUID.fromString(id) }.isSuccess

    private fun publishLocked() {
        _projects.value = documents.values.sortedWith(
            compareByDescending<ProjectDocument> { it.updatedAt }.thenBy { it.name },
        )
    }

    /**
     * Reads the main file and falls back to `.bak`. A document is accepted only when its id
     * matches the file name, which also rejects half-written or renamed files.
     */
    private fun readDocument(file: File): ProjectDocument? {
        if (!file.isFile) return null
        for (text in store.readTexts(file)) {
            val document = runCatching { ProjectJson.decode(text) }.getOrNull() ?: continue
            if (isValidId(document.id) && store.fileFor(document.id).name == file.name) {
                return document
            }
        }
        return null
    }

    private fun writeDocument(project: ProjectDocument) {
        store.write(project.id, ProjectJson.encode(project))
    }
}
