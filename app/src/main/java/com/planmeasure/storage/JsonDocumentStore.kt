package com.planmeasure.storage

import java.io.File
import java.io.FileOutputStream

/**
 * File layer for repositories that keep one JSON document per entity in a directory.
 *
 * Writes are atomic: a temporary file is written, synced and renamed over the target, while the
 * previous version stays in `.bak`. Reading tries the main file first and then the backup.
 * Callers serialize access on their own, usually with a [kotlinx.coroutines.sync.Mutex].
 */
class JsonDocumentStore(private val directory: File) {

    /** Creates the directory and drops temp files left behind by a crash. */
    fun prepare() {
        directory.mkdirs()
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json.tmp") }
            ?.forEach { it.delete() }
    }

    /** Existing documents; main files only, without backups and temp files. */
    fun documents(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.toList()
            .orEmpty()

    fun fileFor(id: String): File = File(directory, "$id.json")

    /**
     * Contents of [file] and its backup, in fallback order. Unreadable candidates are skipped,
     * so a caller can decode each text and accept the first one that makes sense.
     */
    fun readTexts(file: File): List<String> = listOf(file, backupFor(file))
        .filter { it.isFile }
        .mapNotNull { candidate ->
            runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
        }

    fun write(id: String, text: String) {
        directory.mkdirs()
        val target = fileFor(id)
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

    fun delete(id: String) {
        val target = fileFor(id)
        target.delete()
        backupFor(target).delete()
        File(directory, "${target.name}.tmp").delete()
    }

    private fun backupFor(file: File): File = File(directory, "${file.name}.bak")
}
