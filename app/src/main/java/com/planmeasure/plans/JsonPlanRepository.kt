package com.planmeasure.plans

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
 * Stores every plan as one JSON document in [directory], with the same atomic write and backup
 * rules as measurement lists. A single [Mutex] serializes file access, so a saved segment cannot
 * interleave with a rename from another screen.
 */
class JsonPlanRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : PlanRepository {

    private val store = JsonDocumentStore(directory)
    private val mutex = Mutex()
    private val documents = LinkedHashMap<String, PlanDocument>()

    private val _plans = MutableStateFlow<List<PlanDocument>>(emptyList())
    override val plans: StateFlow<List<PlanDocument>> = _plans.asStateFlow()

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

    override suspend fun load(id: String): PlanDocument? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(id)) return@withLock null
            readDocument(store.fileFor(id))
        }
    }

    override fun hasDocument(id: String): Boolean =
        isValidId(id) && store.hasAnyVersion(store.fileFor(id))

    override suspend fun create(
        name: String,
        unit: LengthUnit,
        content: PlanContent,
    ): PlanDocument =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Plan name must not be blank" }
                val now = clock()
                val document = PlanDocument(
                    id = idGenerator(),
                    name = trimmed,
                    unit = unit,
                    createdAt = now,
                    updatedAt = now,
                    nodes = content.nodes,
                    contours = content.contours,
                )
                writeDocument(document)
                documents[document.id] = document
                publishLocked()
                document
            }
        }

    override suspend fun update(plan: PlanDocument) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(plan.id)) return@withLock
            writeDocument(plan)
            documents[plan.id] = plan
            publishLocked()
        }
    }

    override suspend fun rename(id: String, name: String): PlanDocument? =
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
        _plans.value = documents.values.sortedWith(
            compareByDescending<PlanDocument> { it.updatedAt }.thenBy { it.name },
        )
    }

    /**
     * Reads the main file and falls back to `.bak`. A document is accepted only when its id
     * matches the file name, which also rejects half-written or renamed files.
     */
    private fun readDocument(file: File): PlanDocument? {
        if (!file.isFile) return null
        for (text in store.readTexts(file)) {
            val document = runCatching { PlanJson.decode(text) }.getOrNull() ?: continue
            if (isValidId(document.id) && store.fileFor(document.id).name == file.name) {
                return document
            }
        }
        return null
    }

    private fun writeDocument(plan: PlanDocument) {
        store.write(plan.id, PlanJson.encode(plan))
    }
}
