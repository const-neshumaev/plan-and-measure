package com.planmeasure.measurements

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stores every list as one JSON document in [directory].
 *
 * Writes are atomic (temp file + rename) and keep the previous version as `.bak`, which is read
 * when the main file is corrupted. A single [Mutex] serializes all file operations, so a
 * measurement arriving from BLE cannot interleave with an edit from the UI.
 */
class JsonMeasurementListRepository(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : MeasurementListRepository {

    private val mutex = Mutex()
    private val summaries = LinkedHashMap<String, MeasurementListSummary>()

    private val _lists = MutableStateFlow<List<MeasurementListSummary>>(emptyList())
    override val lists: StateFlow<List<MeasurementListSummary>> = _lists.asStateFlow()

    override suspend fun reload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.mkdirs()
            // A crash between writing the temp file and renaming it leaves only a stale copy;
            // the main document is still authoritative.
            directory.listFiles { file -> file.isFile && file.name.endsWith(".json.tmp") }
                ?.forEach { it.delete() }
            summaries.clear()
            files().forEach { file ->
                val document = readDocument(file) ?: return@forEach
                summaries[document.id] = document.summary()
            }
            publishLocked()
        }
    }

    override suspend fun load(id: String): MeasurementList? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(id)) return@withLock null
            readDocument(fileFor(id))
        }
    }

    override suspend fun create(name: String): MeasurementList = withContext(Dispatchers.IO) {
        mutex.withLock {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "List name must not be blank" }
            val now = clock()
            val document = MeasurementList(
                id = idGenerator(),
                name = trimmed,
                createdAt = now,
                updatedAt = now,
            )
            writeDocument(document)
            summaries[document.id] = document.summary()
            publishLocked()
            document
        }
    }

    override suspend fun update(list: MeasurementList) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(list.id)) return@withLock
            writeDocument(list)
            summaries[list.id] = list.summary()
            publishLocked()
        }
    }

    override suspend fun rename(id: String, name: String): MeasurementList? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val trimmed = name.trim()
                if (trimmed.isEmpty() || !isValidId(id)) return@withLock null
                val current = readDocument(fileFor(id)) ?: return@withLock null
                val updated = current.copy(name = trimmed, updatedAt = clock())
                writeDocument(updated)
                summaries[updated.id] = updated.summary()
                publishLocked()
                updated
            }
        }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isValidId(id)) return@withLock
            val target = fileFor(id)
            target.delete()
            backupFor(target).delete()
            File(directory, "${target.name}.tmp").delete()
            summaries.remove(id)
            publishLocked()
        }
    }

    private fun files(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.toList()
            .orEmpty()

    private fun fileFor(id: String): File = File(directory, "$id.json")

    private fun backupFor(file: File): File = File(directory, "${file.name}.bak")

    private fun isValidId(id: String): Boolean = runCatching { UUID.fromString(id) }.isSuccess

    private fun publishLocked() {
        _lists.value = summaries.values.sortedWith(
            compareByDescending<MeasurementListSummary> { it.updatedAt }.thenBy { it.name },
        )
    }

    /**
     * Reads the main file and falls back to `.bak`. A document is accepted only when its id
     * matches the file name, which also rejects half-written or renamed files.
     */
    private fun readDocument(file: File): MeasurementList? {
        if (!file.isFile) return null
        for (candidate in listOf(file, backupFor(file))) {
            if (!candidate.isFile) continue
            val text = runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            val document = runCatching { MeasurementListJson.decode(text) }.getOrNull() ?: continue
            if (isValidId(document.id) && fileFor(document.id).name == file.name) return document
        }
        return null
    }

    private fun writeDocument(list: MeasurementList) {
        directory.mkdirs()
        val target = fileFor(list.id)
        val text = MeasurementListJson.encode(list)
        val temp = File(directory, "${target.name}.tmp")

        FileOutputStream(temp).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            runCatching { stream.fd.sync() }
        }

        if (target.exists()) {
            runCatching { target.copyTo(backupFor(target), overwrite = true) }
        }

        if (!temp.renameTo(target)) {
            // Windows does not replace an existing file with renameTo. Production runs on
            // Android, but unit tests execute this code on a desktop JVM.
            target.writeText(text, Charsets.UTF_8)
            temp.delete()
        }
    }
}
