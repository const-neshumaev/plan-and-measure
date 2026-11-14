package com.planmeasure.logging

import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogKind {
    SYSTEM,
    SCAN,
    GATT,
    READ,
    WRITE,
    NOTIFY,
    MARKER,
    ERROR,
}

/**
 * One line of the research log.
 *
 * The payload is stored as pre-formatted hex and ASCII strings so that entries stay
 * immutable, comparable and cheap to render in Compose.
 */
data class LogEntry(
    val seq: Long,
    val clock: String,
    val elapsedMs: Long,
    val kind: LogKind,
    val source: String,
    val message: String,
    val size: Int = 0,
    val hex: String? = null,
    val ascii: String? = null,
)

/**
 * In-memory append-only log of everything the app does over BLE.
 *
 * Every packet, connection state change and manual marker goes here. Entries are
 * capped to [MAX_ENTRIES] so that long sessions do not exhaust memory; export the log
 * before starting a new experiment if you need more.
 */
class PacketLog(private val file: File? = null) {

    private val lock = Any()
    private val startRealtimeMs = SystemClock.elapsedRealtime()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var nextSeq = 1L

    init {
        // Each process start begins a fresh file so that adb pull always returns the
        // current experiment instead of a mix of sessions.
        val target = file
        if (target != null) {
            runCatching {
                target.parentFile?.mkdirs()
                target.writeText("")
            }
        }
    }

    fun add(
        kind: LogKind,
        source: String,
        message: String,
        data: ByteArray? = null,
    ) {
        synchronized(lock) {
            val nowMs = System.currentTimeMillis()
            val entry = LogEntry(
                seq = nextSeq++,
                clock = timeFormat.format(Date(nowMs)),
                elapsedMs = SystemClock.elapsedRealtime() - startRealtimeMs,
                kind = kind,
                source = source,
                message = message,
                size = data?.size ?: 0,
                hex = data?.let { Hex.encode(it) },
                ascii = data?.let { Hex.encodeAscii(it) },
            )
            _entries.update { current ->
                if (current.size < MAX_ENTRIES) {
                    current + entry
                } else {
                    current.subList(current.size - MAX_ENTRIES + 1, current.size) + entry
                }
            }
            appendToFile(entry)
        }
    }

    private fun appendToFile(entry: LogEntry) {
        val target = file ?: return
        try {
            target.appendText(LogExporter.formatEntry(entry) + "\n")
        } catch (_: IOException) {
            // Best-effort sink: the in-memory log stays authoritative.
        }
    }

    fun marker(text: String) = add(LogKind.MARKER, "MARKER", text)

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 5000
    }
}
