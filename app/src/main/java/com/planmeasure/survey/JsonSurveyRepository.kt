package com.planmeasure.survey

import com.planmeasure.storage.JsonDocumentStore
import com.planmeasure.units.LengthUnit
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
 * Stores every survey as one JSON document in [directory], with the same atomic write and backup
 * rules as measurement lists and plans.
 *
 * The photo of a survey is a sibling file `<id>.jpg` in [photoDirectory]; deleting the document
 * removes it too, so a deleted survey never leaves an orphan photo behind (ADR 0022).
 */
class JsonSurveyRepository(
    directory: File,
    private val photoDirectory: File? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : SurveyRepository {

    private val store = JsonDocumentStore(directory)
    private val mutex = Mutex()
    private val documents = LinkedHashMap<String, SurveyDocument>()

    private val _surveys = MutableStateFlow<List<SurveyDocument>>(emptyList())
    override val surveys: StateFlow<List<SurveyDocument>> = _surveys.asStateFlow()

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

    override suspend fun load(id: String): SurveyDocument? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(id)) return@withLock null
            readDocument(store.fileFor(id))
        }
    }

    override fun hasDocument(id: String): Boolean =
        isValidId(id) && store.hasAnyVersion(store.fileFor(id))

    override suspend fun create(name: String, unit: LengthUnit): SurveyDocument =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Survey name must not be blank" }
                val now = clock()
                val document = SurveyDocument(
                    id = idGenerator(),
                    name = trimmed,
                    unit = unit,
                    createdAt = now,
                    updatedAt = now,
                )
                writeDocument(document)
                documents[document.id] = document
                publishLocked()
                document
            }
        }

    override suspend fun update(survey: SurveyDocument) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(survey.id)) return@withLock
            writeDocument(survey)
            documents[survey.id] = survey
            publishLocked()
        }
    }

    override suspend fun rename(id: String, name: String): SurveyDocument? =
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
            photoDirectory?.let { directory ->
                File(directory, "$id.jpg").delete()
                File(directory, "$id.camera.jpg").delete()
            }
            documents.remove(id)
            publishLocked()
        }
    }

    private fun isValidId(id: String): Boolean = runCatching { UUID.fromString(id) }.isSuccess

    private fun publishLocked() {
        _surveys.value = documents.values.sortedWith(
            compareByDescending<SurveyDocument> { it.updatedAt }.thenBy { it.name },
        )
    }

    /**
     * Reads the main file and falls back to `.bak`. A document is accepted only when its id
     * matches the file name, which also rejects half-written or renamed files.
     */
    private fun readDocument(file: File): SurveyDocument? {
        if (!file.isFile) return null
        for (text in store.readTexts(file)) {
            val document = runCatching { SurveyJson.decode(text) }.getOrNull() ?: continue
            if (isValidId(document.id) && store.fileFor(document.id).name == file.name) {
                return document
            }
        }
        return null
    }

    private fun writeDocument(survey: SurveyDocument) {
        store.write(survey.id, SurveyJson.encode(survey))
    }
}
