package com.planmeasure.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameCodecTest {

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    private fun decode(text: String): DistanceFrame {
        val padded = text.padEnd(FrameCodec.FRAME_LENGTH, ' ')
        val frame = FrameCodec.decode(ascii(padded))
        assertNotNull("Frame must decode: $text", frame)
        return frame!!
    }

    @Test
    fun decodesMetersWithThreeDecimalsOneToOne() {
        val frame = decode("D3.690m")
        assertEquals('D', frame.prefix)
        assertEquals(DistanceFormat.METERS, frame.format)
        assertEquals(DistanceUnit.METERS, frame.unit)
        assertEquals("m", frame.unitText)
        assertEquals("3.690", frame.valueText)
        assertEquals(3.690, frame.normalizedValue, 1e-9)
        assertEquals("3.690", frame.normalizedText)
        assertEquals(16, frame.raw.length)
    }

    @Test
    fun shiftsMetersWithTwoDecimals() {
        // Captured 2026-11-17 at the same point as D0.828m: display 0.828 m, frame D8.28m.
        val near = decode("D8.28m")
        assertEquals(DistanceFormat.METERS_SHIFTED, near.format)
        assertEquals(0.828, near.normalizedValue, 1e-9)
        assertEquals("0.828", near.normalizedText)

        // Display 4.061 m, frame D40.61m.
        val far = decode("D40.61m")
        assertEquals(4.061, far.normalizedValue, 1e-9)
        assertEquals("4.061", far.normalizedText)

        // Earlier captured two-decimal meter frames: 2026-11-17 18:59 and 20:10.
        assertEquals(3.261, decode("D32.61m").normalizedValue, 1e-9)
        assertEquals(3.66, decode("D36.60m").normalizedValue, 1e-9)
        assertEquals(0.4, decode("D4.00m").normalizedValue, 1e-9)
    }

    @Test
    fun shiftsFeet() {
        // Display 11.961 ft, frame D119.61ft.
        val frame = decode("D119.61ft")
        assertEquals(DistanceFormat.FEET, frame.format)
        assertEquals(DistanceUnit.FEET, frame.unit)
        assertEquals("119.61", frame.valueText)
        assertEquals(11.961, frame.normalizedValue, 1e-9)
        assertEquals("11.961", frame.normalizedText)
    }

    @Test
    fun trimsTrailingZerosOfShiftedFeet() {
        val frame = decode("D29.00ft")
        assertEquals(2.9, frame.normalizedValue, 1e-9)
        assertEquals("2.9", frame.normalizedText)
    }

    @Test
    fun decodesFeetInches() {
        // Display 11'08" 3/16.
        val frame = decode("D11.08ftin  3/16")
        assertEquals(DistanceFormat.FEET_INCHES, frame.format)
        assertEquals(DistanceUnit.FEET, frame.unit)
        assertEquals("11.08", frame.valueText)
        assertEquals("3/16", frame.fractionText)
        assertEquals(11.6823, frame.normalizedValue, 1e-4)
        assertEquals("11.6823", frame.normalizedText)

        // Display 5'04" 1/2.
        assertEquals(5.375, decode("D5.04ftin   1/ 2").normalizedValue, 1e-9)

        // Display 19'01" 1/2.
        assertEquals(19.125, decode("D19.01ftin  1/ 2").normalizedValue, 1e-9)

        // Display 0'11" 1/8.
        assertEquals(0.9271, decode("D0.11ftin   1/ 8").normalizedValue, 1e-4)
    }

    @Test
    fun decodesFeetInchesWithQuotes() {
        // Display 12'07" 5/16.
        val frame = decode("D12'07\"     5/16")
        assertEquals(DistanceFormat.FEET_INCHES_QUOTE, frame.format)
        assertEquals(DistanceUnit.FEET, frame.unit)
        assertEquals(12.6094, frame.normalizedValue, 1e-4)
        assertEquals("12.6094", frame.normalizedText)

        // Display 3'05" without a fraction.
        assertEquals(3.4167, decode("D3'05\"").normalizedValue, 1e-4)

        // Display 9'08" 1/4.
        assertEquals(9.6875, decode("D9'08\"      1/ 4").normalizedValue, 1e-9)

        // Display 3'05" 13/16.
        assertEquals(3.4844, decode("D3'05\"     13/16").normalizedValue, 1e-4)

        // Display 19'09" 1/4.
        assertEquals(19.7708, decode("D19'09\"     1/ 4").normalizedValue, 1e-4)
    }

    @Test
    fun shiftsDecimalInchesByTwoPlaces() {
        // Display 159.879 in: the two leading digits are packed into one character (15 -> '?').
        val frame = decode("D?987.9in")
        assertEquals(DistanceFormat.INCHES_DECIMAL, frame.format)
        assertEquals(DistanceUnit.INCHES, frame.unit)
        assertEquals("?987.9", frame.valueText)
        assertEquals(159.879, frame.normalizedValue, 1e-9)
        assertEquals("159.879", frame.normalizedText)

        // 10 -> ':', 22 -> 'F', 14 -> '>'.
        assertEquals(105.313, decode("D:531.3in").normalizedValue, 1e-9)
        assertEquals(226.257, decode("DF625.7in").normalizedValue, 1e-9)
        assertEquals(154.289, decode("D?428.9in").normalizedValue, 1e-9)

        // No packed leading digits: the first two digits are normal characters.
        assertEquals(42.086, decode("D4208.6in").normalizedValue, 1e-9)
        assertEquals(32.598, decode("D3259.8in").normalizedValue, 1e-9)
        assertEquals(31.692, decode("D3169.2in").normalizedValue, 1e-9)
    }

    @Test
    fun decodesInchesWithFractionAndZeroPlaceholders() {
        // Display 32 5/8 in.
        val frame = decode("D32000in    5/ 8")
        assertEquals(DistanceFormat.INCHES_FRACTION, frame.format)
        assertEquals(DistanceUnit.INCHES, frame.unit)
        assertEquals("32000", frame.valueText)
        assertEquals("5/8", frame.fractionText)
        assertEquals(32.625, frame.normalizedValue, 1e-9)
        assertEquals("32.625", frame.normalizedText)

        assertEquals(42.0625, decode("D42000in    1/16").normalizedValue, 1e-9)
        assertEquals(52.75, decode("D52000in    3/ 4").normalizedValue, 1e-9)
        assertEquals(159.875, decode("D?9000in    7/ 8").normalizedValue, 1e-9)
        assertEquals(147.0625, decode("D>7000in    1/16").normalizedValue, 1e-9)
        assertEquals(158.4375, decode("D?8000in    7/16").normalizedValue, 1e-9)

        // Reported from the app on 2026-11-17: display 149 1/16 in, frame D>9000in    1/16.
        assertEquals(149.0625, decode("D>9000in    1/16").normalizedValue, 1e-9)
    }

    @Test
    fun decodesInchesWithoutFraction() {
        // Display 1435 in, no BLE capture: the whole inches may keep the zero placeholders.
        assertEquals(1435.0, decode("D1435in").normalizedValue, 1e-9)
        assertEquals(1435.0, decode("D1435000in").normalizedValue, 1e-9)

        // Old frame D?9000in: `?` packs 15, so the display was 159 in with a zero fraction.
        // This matches the user's display marker 159 7/8 for the same point.
        assertEquals(159.0, decode("D?9000in").normalizedValue, 1e-9)
    }

    @Test
    fun samePointMeasurementsAgree() {
        // 2026-11-17: one fixed point measured in four modes.
        val meters = decode("D0.828m").normalizedValue
        val metersShifted = decode("D8.28m").normalizedValue
        val inchesDecimal = decode("D3259.8in").normalizedValue
        val inchesFraction = decode("D32000in    5/ 8").normalizedValue

        assertEquals(meters, metersShifted, 1e-9)
        assertEquals(meters * 1000.0 / 25.4, inchesDecimal, 1e-3)
        assertEquals(meters * 1000.0 / 25.4, inchesFraction, 0.05)

        // 4.061 m measured as D4.061m and D40.61m.
        assertEquals(
            decode("D4.061m").normalizedValue,
            decode("D40.61m").normalizedValue,
            1e-9,
        )

        // 159.879 in measured as decimal inches and as 159 7/8 in.
        assertEquals(
            decode("D?987.9in").normalizedValue,
            decode("D?9000in    7/ 8").normalizedValue,
            0.01,
        )
    }

    @Test
    fun convertsToMillimeters() {
        assertEquals(828.0, decode("D0.828m").valueMm!!, 1e-6)
        assertEquals(828.675, decode("D32000in    5/ 8").valueMm!!, 1e-4)
        assertEquals(2060.7528, decode("D67.61ft").valueMm!!, 1e-4)
    }

    @Test
    fun decodesMillimetersForCompleteness() {
        val frame = decode("D1.5mm")
        assertEquals(DistanceUnit.MILLIMETERS, frame.unit)
        assertEquals(1.5, frame.normalizedValue, 1e-9)
        assertEquals(1.5, frame.valueMm!!, 1e-9)
    }

    @Test
    fun decodesAllCapturedMeterFrames() {
        val expected = listOf(
            "D1.479m" to 1.479,
            "D3.781m" to 3.781,
            "D1.355m" to 1.355,
            "D1.686m" to 1.686,
            "D1.261m" to 1.261,
            "D1.421m" to 1.421,
            "D2.768m" to 2.768,
            "D1.726m" to 1.726,
            "D3.471m" to 3.471,
            "D1.624m" to 1.624,
            "D3.384m" to 3.384,
            "D1.611m" to 1.611,
            "D3.695m" to 3.695,
            "D0.293m" to 0.293,
            "D0.297m" to 0.297,
            "D1.142m" to 1.142,
            "D1.132m" to 1.132,
            "D1.148m" to 1.148,
            "D1.324m" to 1.324,
            "D3.930m" to 3.930,
            "D2.014m" to 2.014,
            "D0.828m" to 0.828,
            "D3.690m" to 3.690,
            "D4.061m" to 4.061,
        )
        for ((raw, value) in expected) {
            val frame = decode(raw)
            assertEquals(value, frame.normalizedValue, 1e-9)
            assertEquals(DistanceUnit.METERS, frame.unit)
        }
    }

    @Test
    fun trimsNullAndSpacePadding() {
        val frame = FrameCodec.decode(ascii("D2.000m ") + byteArrayOf(0, 0))
        assertNotNull(frame)
        assertEquals(2.0, frame!!.normalizedValue, 1e-9)
    }

    @Test
    fun rejectsPlaceholderAndGarbage() {
        assertNull(FrameCodec.decode(ascii("D---m           ")))
        assertNull(FrameCodec.decode(ByteArray(0)))
        assertNull(FrameCodec.decode(ascii("hello world     ")))
        assertNull(FrameCodec.decode(ascii("D12'13\"         ")))
        assertNull(FrameCodec.decode(ascii("D1.5m           ")))
    }

    @Test
    fun keepsRawPaddingForAnalysis() {
        val frame = decode("D1.000m")
        assertEquals("D1.000m         ", frame.raw)
    }
}
