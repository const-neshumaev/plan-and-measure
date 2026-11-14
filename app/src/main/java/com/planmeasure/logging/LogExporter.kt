package com.planmeasure.logging

/**
 * Converts captured traffic into artifacts that can be shared with the analysis step
 * (spreadsheet, AI agent, notes).
 */
object LogExporter {

    /**
     * Builds a CSV file with a `#`-prefixed metadata header.
     *
     * The header keeps device/MTU/Android details next to the frames, which is important:
     * the same device may pack data differently depending on the negotiated MTU.
     */
    fun toCsv(metadata: List<Pair<String, String>>, entries: List<LogEntry>): String {
        val out = StringBuilder()
        for ((key, value) in metadata) {
            out.append("# ").append(key).append(": ").append(value).append('\n')
        }
        out.append("seq,clock,elapsed_ms,kind,source,message,size,hex,ascii\n")
        for (entry in entries) {
            out.append(entry.seq).append(',')
            out.append(escape(entry.clock)).append(',')
            out.append(entry.elapsedMs).append(',')
            out.append(entry.kind.name).append(',')
            out.append(escape(entry.source)).append(',')
            out.append(escape(entry.message)).append(',')
            out.append(entry.size).append(',')
            out.append(escape(entry.hex.orEmpty())).append(',')
            out.append(escape(entry.ascii.orEmpty())).append('\n')
        }
        return out.toString()
    }

    /** Human-readable log used for the clipboard and for pasting into chat with the agent. */
    fun toText(entries: List<LogEntry>): String {
        val out = StringBuilder()
        for (entry in entries) {
            out.append(formatEntry(entry)).append('\n')
        }
        return out.toString()
    }

    /** One log line without the trailing newline. Shared by the clipboard and the file sink. */
    fun formatEntry(entry: LogEntry): String {
        val out = StringBuilder()
        out.append('#').append(entry.seq)
            .append(' ').append(entry.clock)
            .append(" +").append(entry.elapsedMs).append("ms")
            .append(' ').append(entry.kind.name)
            .append(' ').append(entry.source)
            .append(": ").append(entry.message)
        entry.hex?.let { out.append(" | ").append(it) }
        entry.ascii?.let { out.append(" |").append(it).append('|') }
        return out.toString()
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }
}
