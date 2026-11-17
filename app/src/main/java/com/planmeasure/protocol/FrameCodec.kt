package com.planmeasure.protocol

import java.math.BigDecimal
import java.math.RoundingMode

enum class DistanceUnit {
    METERS,
    FEET,
    INCHES,
    MILLIMETERS,
    UNKNOWN,
}

/** Layout of the numeric field; some firmwares place the decimal point incorrectly. */
enum class DistanceFormat {
    METERS,
    METERS_SHIFTED,
    FEET,
    FEET_INCHES,
    FEET_INCHES_QUOTE,
    INCHES_DECIMAL,
    INCHES_FRACTION,
    UNKNOWN,
}

/**
 * One frame received from characteristic 0xCBB1.
 *
 * The device mirrors what it shows on its display: an ASCII string padded with spaces
 * to 16 bytes, for example `D1.763m         ` or `D72.60ft        `.
 *
 * Only completed measurements are transmitted. Area, volume and Pythagoras results are
 * computed on the device and never sent; the app receives the individual distances.
 */
data class DistanceFrame(
    val raw: String,
    val prefix: Char,
    val format: DistanceFormat,
    val unit: DistanceUnit,
    val unitText: String,
    /** Numeric field exactly as printed, without the fraction: `1.763`, `8.28`, `?987.9`. */
    val valueText: String,
    /** Fraction without spaces, e.g. `5/8`; `null` when the frame has none. */
    val fractionText: String? = null,
    /** Value in [unit] with the firmware's decimal point shift corrected. */
    val normalizedValue: Double,
    /** Decimal text in [unit] used for storage; keeps the device precision. */
    val normalizedText: String,
) {

    /** Distance in millimeters, or `null` when the unit is unknown. */
    val valueMm: Double?
        get() = when (unit) {
            DistanceUnit.METERS -> normalizedValue * 1000.0
            DistanceUnit.FEET -> normalizedValue * 304.8
            DistanceUnit.INCHES -> normalizedValue * 25.4
            DistanceUnit.MILLIMETERS -> normalizedValue
            DistanceUnit.UNKNOWN -> null
        }
}

/**
 * Decodes notifications of `0xCBB1`.
 *
 * The device has seven unit modes: meters with three decimals, meters with two decimals,
 * feet, feet-inches (`ftin`), feet-inches with quotes (`' "`), decimal inches and inches
 * with a fraction. Three of them shift the decimal point to the right, and the inch modes
 * may pack the two leading digits into a single character. See `docs/protocol-research/protocol.md`.
 */
object FrameCodec {

    const val FRAME_LENGTH = 16

    /** Fractions may carry a space after the slash: `1/ 8`. */
    private val fractionPattern = Regex("""(\d+)\s*/\s*(\d+)\s*$""")
    private val feetInchesPattern = Regex("""^(\d+)\.(\d{2})$""")
    private val quotePattern = Regex("""^(\d+)'(\d{2})"$""")

    /**
     * Decodes a notification payload. Returns `null` for placeholders such as `D---m`,
     * for unconfirmed variants, and for anything that does not look like a measurement.
     */
    fun decode(bytes: ByteArray): DistanceFrame? {
        if (bytes.isEmpty()) return null
        val raw = String(bytes, Charsets.US_ASCII)
        val trimmed = raw.trimEnd { it == ' ' || it == '\u0000' }
        if (trimmed.length < 2) return null
        val prefix = trimmed[0]
        if (!prefix.isLetter()) return null

        var body = trimmed.substring(1)
        val fractionMatch = fractionPattern.find(body)?.takeIf { it.range.last == body.lastIndex }
        val fractionText = fractionMatch?.let {
            "${it.groupValues[1]}/${it.groupValues[2]}"
        }
        if (fractionMatch != null) {
            body = body.substring(0, fractionMatch.range.first).trimEnd()
        }

        quotePattern.matchEntire(body)?.let { match ->
            val feet = match.groupValues[1].toIntOrNull() ?: return null
            val inches = match.groupValues[2].toIntOrNull() ?: return null
            return feetInchesFrame(
                raw = raw,
                prefix = prefix,
                valueText = body,
                unitText = "' \"",
                feet = feet,
                inches = inches,
                fractionText = fractionText,
                format = DistanceFormat.FEET_INCHES_QUOTE,
            )
        }

        val (unitText, printedNumber) = when {
            body.endsWith("ftin") -> "ftin" to body.dropLast(4)
            body.endsWith("in") -> "in" to body.dropLast(2)
            body.endsWith("ft") -> "ft" to body.dropLast(2)
            body.endsWith("mm") -> "mm" to body.dropLast(2)
            body.endsWith("m") -> "m" to body.dropLast(1)
            else -> return null
        }
        val rawNumber = printedNumber.trimEnd()
        // The inch layouts pack the two leading digits of a long value into one character;
        // the other layouts never use that packing.
        val number = if (unitText == "in") expandLeadingDigits(rawNumber) else rawNumber
        if (number.isEmpty()) return null

        return when (unitText) {
            "ftin" -> {
                val match = feetInchesPattern.matchEntire(number) ?: return null
                val feet = match.groupValues[1].toIntOrNull() ?: return null
                val inches = match.groupValues[2].toIntOrNull() ?: return null
                feetInchesFrame(
                    raw = raw,
                    prefix = prefix,
                    valueText = rawNumber,
                    unitText = unitText,
                    feet = feet,
                    inches = inches,
                    fractionText = fractionText,
                    format = DistanceFormat.FEET_INCHES,
                )
            }

            "in" -> when {
                fractionText != null -> inchesFractionFrame(raw, prefix, rawNumber, number, fractionText)
                number.contains('.') -> inchesDecimalFrame(raw, prefix, rawNumber, number)
                else -> inchesWithoutFractionFrame(raw, prefix, rawNumber, number)
            }

            "ft" -> if (decimalPlaces(number) == 2) {
                shiftedFrame(
                    raw = raw,
                    prefix = prefix,
                    valueText = rawNumber,
                    number = number,
                    unitText = unitText,
                    unit = DistanceUnit.FEET,
                    format = DistanceFormat.FEET,
                    shift = 1,
                )
            } else {
                null
            }

            "mm" -> {
                val value = number.toBigDecimalOrNull() ?: return null
                plainFrame(
                    raw = raw,
                    prefix = prefix,
                    valueText = rawNumber,
                    number = number,
                    unitText = unitText,
                    unit = DistanceUnit.MILLIMETERS,
                    format = DistanceFormat.UNKNOWN,
                    value = value,
                )
            }

            "m" -> when (decimalPlaces(number)) {
                3 -> {
                    val value = number.toBigDecimalOrNull() ?: return null
                    plainFrame(
                        raw = raw,
                        prefix = prefix,
                        valueText = rawNumber,
                        number = number,
                        unitText = unitText,
                        unit = DistanceUnit.METERS,
                        format = DistanceFormat.METERS,
                        value = value,
                    )
                }

                2 -> shiftedFrame(
                    raw = raw,
                    prefix = prefix,
                    valueText = rawNumber,
                    number = number,
                    unitText = unitText,
                    unit = DistanceUnit.METERS,
                    format = DistanceFormat.METERS_SHIFTED,
                    shift = 1,
                )

                else -> null
            }

            else -> null
        }
    }

    fun unitOf(text: String): DistanceUnit = when (text.lowercase()) {
        "m" -> DistanceUnit.METERS
        "ft" -> DistanceUnit.FEET
        "ftin" -> DistanceUnit.FEET
        "in" -> DistanceUnit.INCHES
        "mm" -> DistanceUnit.MILLIMETERS
        else -> DistanceUnit.UNKNOWN
    }

    private fun plainFrame(
        raw: String,
        prefix: Char,
        valueText: String,
        number: String,
        unitText: String,
        unit: DistanceUnit,
        format: DistanceFormat,
        value: BigDecimal,
    ) = DistanceFrame(
        raw = raw,
        prefix = prefix,
        format = format,
        unit = unit,
        unitText = unitText,
        valueText = valueText,
        normalizedValue = value.toDouble(),
        normalizedText = number,
    )

    private fun shiftedFrame(
        raw: String,
        prefix: Char,
        valueText: String,
        number: String,
        unitText: String,
        unit: DistanceUnit,
        format: DistanceFormat,
        shift: Int,
    ): DistanceFrame? {
        val value = number.toBigDecimalOrNull() ?: return null
        val normalized = value.movePointLeft(shift).stripTrailingZeros()
        return DistanceFrame(
            raw = raw,
            prefix = prefix,
            format = format,
            unit = unit,
            unitText = unitText,
            valueText = valueText,
            normalizedValue = normalized.toDouble(),
            normalizedText = normalized.toPlainString(),
        )
    }

    /** Decimal inches are printed as the value multiplied by 100 with one decimal. */
    private fun inchesDecimalFrame(
        raw: String,
        prefix: Char,
        valueText: String,
        number: String,
    ): DistanceFrame? {
        if (decimalPlaces(number) != 1) return null
        return shiftedFrame(
            raw = raw,
            prefix = prefix,
            valueText = valueText,
            number = number,
            unitText = "in",
            unit = DistanceUnit.INCHES,
            format = DistanceFormat.INCHES_DECIMAL,
            shift = 2,
        )
    }

    /**
     * A fractional-inch frame with no fraction (seen as `1435 in` on the display, no BLE capture):
     * the whole inches may still carry the three zero placeholders.
     */
    private fun inchesWithoutFractionFrame(
        raw: String,
        prefix: Char,
        valueText: String,
        number: String,
    ): DistanceFrame? {
        if (!number.all { it.isDigit() }) return null
        val wholeText = if (number.length >= 4 && number.endsWith("000")) number.dropLast(3) else number
        val whole = wholeText.toBigDecimalOrNull() ?: return null
        return DistanceFrame(
            raw = raw,
            prefix = prefix,
            format = DistanceFormat.INCHES_FRACTION,
            unit = DistanceUnit.INCHES,
            unitText = "in",
            valueText = valueText,
            normalizedValue = whole.toDouble(),
            normalizedText = whole.toPlainString(),
        )
    }

    /**
     * The fractional inch layout prints whole inches followed by three zero placeholders,
     * for example `32000in    5/ 8` is `32 5/8 in`.
     */
    private fun inchesFractionFrame(
        raw: String,
        prefix: Char,
        valueText: String,
        number: String,
        fractionText: String,
    ): DistanceFrame? {
        if (number.length < 4 || !number.all { it.isDigit() } || !number.endsWith("000")) {
            return null
        }
        val whole = number.dropLast(3).toBigDecimalOrNull() ?: return null
        val fraction = fractionValue(fractionText) ?: return null
        val normalized = whole.add(fraction).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
        return DistanceFrame(
            raw = raw,
            prefix = prefix,
            format = DistanceFormat.INCHES_FRACTION,
            unit = DistanceUnit.INCHES,
            unitText = "in",
            valueText = valueText,
            fractionText = fractionText,
            normalizedValue = normalized.toDouble(),
            normalizedText = normalized.toPlainString(),
        )
    }

    private fun feetInchesFrame(
        raw: String,
        prefix: Char,
        valueText: String,
        unitText: String,
        feet: Int,
        inches: Int,
        fractionText: String?,
        format: DistanceFormat,
    ): DistanceFrame? {
        if (inches !in 0..11) return null
        val fraction = fractionValue(fractionText) ?: return null
        val inchPart = BigDecimal(inches).add(fraction)
        val normalized = BigDecimal(feet)
            .add(inchPart.divide(BigDecimal(12), 6, RoundingMode.HALF_UP))
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return DistanceFrame(
            raw = raw,
            prefix = prefix,
            format = format,
            unit = DistanceUnit.FEET,
            unitText = unitText,
            valueText = valueText,
            fractionText = fractionText,
            normalizedValue = normalized.toDouble(),
            normalizedText = normalized.toPlainString(),
        )
    }

    private fun fractionValue(text: String?): BigDecimal? {
        if (text == null) return BigDecimal.ZERO
        val parts = text.split('/')
        if (parts.size != 2) return null
        val numerator = parts[0].toIntOrNull() ?: return null
        val denominator = parts[1].toIntOrNull() ?: return null
        if (denominator == 0) return null
        return BigDecimal(numerator).divide(BigDecimal(denominator), 4, RoundingMode.HALF_UP)
    }

    /**
     * The firmware packs the first two digits of a long value into one character: the
     * character code is `'0' + firstTwoDigits`. So `:` is 10, `>` is 14, `?` is 15,
     * `F` is 22, and `D>9000in    1/16` means 149 1/16 in. Normal digits are unchanged.
     */
    private fun expandLeadingDigits(text: String): String {
        if (text.isEmpty()) return text
        val packed = text[0].code - '0'.code
        if (packed !in 10..99) return text
        return packed.toString() + text.substring(1)
    }

    private fun decimalPlaces(text: String): Int = text.substringAfter('.', "").length
}
