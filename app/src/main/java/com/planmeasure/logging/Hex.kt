package com.planmeasure.logging

/**
 * Hex helpers used by the logger and by the future protocol codec.
 *
 * All methods are side-effect free and safe to call from any thread.
 */
object Hex {

    private const val HEX_DIGITS = "0123456789ABCDEF"

    /** Encodes bytes as uppercase hex pairs separated by single spaces, e.g. `AA 55 01`. */
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(bytes.size * 3)
        for (index in bytes.indices) {
            if (index > 0) out.append(' ')
            val value = bytes[index].toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4])
            out.append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    /** Prints bytes as printable ASCII, replacing non-printable bytes with a dot. */
    fun encodeAscii(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(bytes.size)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append(if (value in 0x20..0x7E) value.toChar() else '.')
        }
        return out.toString()
    }

    /**
     * Parses a hex string with optional whitespace, `-` and `:` separators.
     * Returns `null` when the input is empty, has an odd number of digits,
     * or contains non-hex characters.
     */
    fun parse(text: String): ByteArray? {
        val cleaned = StringBuilder(text.length)
        for (char in text) {
            if (char.isWhitespace() || char == '-' || char == ':') continue
            cleaned.append(char)
        }
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        val result = ByteArray(cleaned.length / 2)
        for (index in result.indices) {
            val high = Character.digit(cleaned[index * 2], 16)
            val low = Character.digit(cleaned[index * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            result[index] = ((high shl 4) or low).toByte()
        }
        return result
    }
}
