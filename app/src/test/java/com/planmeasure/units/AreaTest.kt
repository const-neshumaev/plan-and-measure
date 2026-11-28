package com.planmeasure.units

import org.junit.Assert.assertEquals
import org.junit.Test

class AreaTest {

    @Test
    fun formatsSquareMeters() {
        assertEquals("12", Area.format(12_000_000.0, LengthUnit.METERS))
        assertEquals("1.5", Area.format(1_500_000.0, LengthUnit.METERS))
    }

    @Test
    fun convertsToTheSquareOfTheDocumentUnit() {
        // One square meter is ten thousand square centimeters.
        assertEquals("10000", Area.format(1_000_000.0, LengthUnit.CENTIMETERS))
    }

    @Test
    fun keepsZeroReadable() {
        assertEquals("0", Area.format(0.0, LengthUnit.METERS))
    }

    @Test
    fun rejectsNonFiniteValues() {
        assertEquals("", Area.format(Double.NaN, LengthUnit.METERS))
    }
}
