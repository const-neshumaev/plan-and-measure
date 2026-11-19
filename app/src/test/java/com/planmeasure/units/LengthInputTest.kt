package com.planmeasure.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LengthInputTest {

    @Test
    fun acceptsDotAndCommaSeparators() {
        assertEquals("1763", LengthInput.normalize("1.763", LengthUnit.METERS))
        assertEquals("1763", LengthInput.normalize("1,763", LengthUnit.METERS))
    }

    @Test
    fun treatsTrailingSeparatorAsWholeNumber() {
        assertEquals("10000", LengthInput.normalize("10.", LengthUnit.METERS))
        assertEquals("10000", LengthInput.normalize("10,", LengthUnit.METERS))
        assertNull(LengthInput.normalize(".", LengthUnit.METERS))
    }

    @Test
    fun filtersCharactersThatCannotBelongToAValue() {
        assertEquals("10", LengthInput.filter("1a0", LengthUnit.METERS))
        assertEquals("12.34", LengthInput.filter("12.3.4", LengthUnit.METERS))
        assertEquals("1,2", LengthInput.filter("1,2345", LengthUnit.CENTIMETERS))
        assertEquals("0.5", LengthInput.filter(".5", LengthUnit.METERS))
        assertEquals("123456", LengthInput.filter("1234567", LengthUnit.METERS))
    }

    @Test
    fun normalizesLeadingZerosAndSpaces() {
        assertEquals("1500", LengthInput.normalize("  01.5 ", LengthUnit.METERS))
        assertEquals("1", LengthInput.normalize("00.001", LengthUnit.METERS))
    }

    @Test
    fun ignoresTrailingZerosBeyondTheUnitPrecision() {
        assertEquals("1500", LengthInput.normalize("1.500", LengthUnit.METERS))
        assertEquals("15", LengthInput.normalize("1.500", LengthUnit.CENTIMETERS))
    }

    @Test
    fun convertsToCanonicalMillimeters() {
        assertEquals("123", LengthInput.normalize("12.3", LengthUnit.CENTIMETERS))
        assertEquals("20", LengthInput.normalize("20", LengthUnit.MILLIMETERS))
        assertEquals("304.8", LengthInput.normalize("1", LengthUnit.FEET))
        assertEquals("1.5875", LengthInput.normalize("0.0625", LengthUnit.INCHES))
    }

    @Test
    fun rejectsMoreDecimalsThanTheUnitShows() {
        assertEquals("1234", LengthInput.normalize("1.234", LengthUnit.METERS))
        assertNull(LengthInput.normalize("1.2345", LengthUnit.METERS))
        assertNull(LengthInput.normalize("1.23", LengthUnit.CENTIMETERS))
        assertNull(LengthInput.normalize("1.234", LengthUnit.CENTIMETERS))
        assertNull(LengthInput.normalize("20.05", LengthUnit.MILLIMETERS))
        assertEquals("304.8", LengthInput.normalize("1.0000", LengthUnit.FEET))
        assertNull(LengthInput.normalize("1.00001", LengthUnit.FEET))
    }

    @Test
    fun rejectsNonPositiveValues() {
        assertNull(LengthInput.normalize("0", LengthUnit.METERS))
        assertNull(LengthInput.normalize("0.000", LengthUnit.METERS))
        assertNull(LengthInput.normalize("-1.2", LengthUnit.METERS))
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(LengthInput.normalize("", LengthUnit.METERS))
        assertNull(LengthInput.normalize(".", LengthUnit.METERS))
        assertNull(LengthInput.normalize(".5", LengthUnit.METERS))
        assertNull(LengthInput.normalize("1234567", LengthUnit.METERS))
        assertNull(LengthInput.normalize("1,234,5", LengthUnit.METERS))
        assertNull(LengthInput.normalize("width", LengthUnit.METERS))
    }
}
