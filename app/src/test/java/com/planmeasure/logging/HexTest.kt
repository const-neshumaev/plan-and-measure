package com.planmeasure.logging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexTest {

    @Test
    fun encodeFormatsUppercasePairsWithSpaces() {
        assertEquals("AA 55 01", Hex.encode(byteArrayOf(0xAA.toByte(), 0x55, 0x01)))
    }

    @Test
    fun encodeReturnsEmptyStringForEmptyInput() {
        assertEquals("", Hex.encode(ByteArray(0)))
    }

    @Test
    fun encodeAsciiReplacesNonPrintableBytes() {
        assertEquals("A..", Hex.encodeAscii(byteArrayOf(0x41, 0x00, 0x7F)))
    }

    @Test
    fun parseAcceptsSpacesDashesAndColons() {
        val expected = byteArrayOf(0xAA.toByte(), 0x55, 0x01)
        assertArrayEquals(expected, Hex.parse("AA 55 01"))
        assertArrayEquals(expected, Hex.parse("aa-55-01"))
        assertArrayEquals(expected, Hex.parse("AA:55:01"))
        assertArrayEquals(expected, Hex.parse("aa5501"))
    }

    @Test
    fun parseRejectsOddLengthAndNonHexInput() {
        assertNull(Hex.parse(""))
        assertNull(Hex.parse("A"))
        assertNull(Hex.parse("ZZ"))
        assertNull(Hex.parse("AA 5"))
    }

    @Test
    fun parseAndEncodeRoundTrip() {
        val original = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val encoded = Hex.encode(original)
        assertArrayEquals(original, Hex.parse(encoded))
    }
}
