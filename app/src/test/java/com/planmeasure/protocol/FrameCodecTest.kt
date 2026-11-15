package com.planmeasure.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrameCodecTest {

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun decodesMetersFrame() {
        val frame = FrameCodec.decode(ascii("D1.763m         "))
        assertNotNull(frame)
        assertEquals('D', frame!!.prefix)
        assertEquals(1.763, frame.value, 1e-9)
        assertEquals("1.763", frame.valueText)
        assertEquals(DistanceUnit.METERS, frame.unit)
        assertEquals("m", frame.unitText)
        assertEquals(16, frame.raw.length)
    }

    @Test
    fun decodesFeetFrameWithTwoDecimals() {
        val frame = FrameCodec.decode(ascii("D72.60ft        "))
        assertNotNull(frame)
        assertEquals(72.60, frame!!.value, 1e-9)
        assertEquals("72.60", frame.valueText)
        assertEquals(DistanceUnit.FEET, frame.unit)
        assertEquals("ft", frame.unitText)
        assertEquals(7.260, frame.normalizedValue, 1e-9)
    }

    @Test
    fun normalizesFeetDecimalPointShift() {
        // Captured during the 2026-11-14 session: display 6.76 ft, frame D67.61ft.
        val first = FrameCodec.decode(ascii("D67.61ft        "))!!
        assertEquals(6.761, first.normalizedValue, 1e-9)
        assertEquals("6.761", first.normalizedText)

        // Display 11.89 ft, frame D118.89ft.
        val second = FrameCodec.decode(ascii("D118.89ft       "))!!
        assertEquals(11.889, second.normalizedValue, 1e-9)
        assertEquals("11.889", second.normalizedText)
    }

    @Test
    fun normalizedTextTrimsFeetTrailingZero() {
        val frame = FrameCodec.decode(ascii("D72.60ft        "))!!
        assertEquals("7.26", frame.normalizedText)
    }

    @Test
    fun normalizedTextLeavesMetersUntouched() {
        val frame = FrameCodec.decode(ascii("D1.760m         "))!!
        assertEquals("1.760", frame.normalizedText)
    }

    @Test
    fun convertsToMillimeters() {
        val meters = FrameCodec.decode(ascii("D1.763m         "))!!
        assertEquals(1763.0, meters.valueMm!!, 1e-6)

        val feet = FrameCodec.decode(ascii("D67.61ft        "))!!
        assertEquals(2060.7528, feet.valueMm!!, 1e-4)
    }

    @Test
    fun decodesAllCapturedMeterFrames() {
        val expected = listOf(
            "D1.479m         " to 1.479,
            "D3.781m         " to 3.781,
            "D1.355m         " to 1.355,
            "D1.686m         " to 1.686,
            "D1.261m         " to 1.261,
            "D1.421m         " to 1.421,
            "D2.768m         " to 2.768,
            "D1.726m         " to 1.726,
            "D3.471m         " to 3.471,
            "D1.624m         " to 1.624,
            "D3.384m         " to 3.384,
            "D1.611m         " to 1.611,
        )
        for ((raw, value) in expected) {
            val frame = FrameCodec.decode(ascii(raw))
            assertNotNull("Frame must decode: $raw", frame)
            assertEquals(value, frame!!.value, 1e-9)
            assertEquals(DistanceUnit.METERS, frame.unit)
        }
    }

    @Test
    fun trimsNullAndSpacePadding() {
        val frame = FrameCodec.decode(ascii("D2.000m ") + byteArrayOf(0, 0))
        assertNotNull(frame)
        assertEquals(2.0, frame!!.value, 1e-9)
    }

    @Test
    fun rejectsPlaceholderAndGarbage() {
        assertNull(FrameCodec.decode(ascii("D---m           ")))
        assertNull(FrameCodec.decode(ByteArray(0)))
        assertNull(FrameCodec.decode(ascii("hello world     ")))
    }

    @Test
    fun keepsRawPaddingForAnalysis() {
        val frame = FrameCodec.decode(ascii("D1.000m         "))
        assertNotNull(frame)
        assertEquals("D1.000m         ", frame!!.raw)
    }
}
