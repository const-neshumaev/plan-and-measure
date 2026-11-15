package com.planmeasure.measurements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementInputTest {

    @Test
    fun acceptsDotAndCommaSeparators() {
        assertEquals("1.763", MeasurementInput.normalize("1.763"))
        assertEquals("1.763", MeasurementInput.normalize("1,763"))
    }

    @Test
    fun normalizesLeadingZerosAndSpaces() {
        assertEquals("1.5", MeasurementInput.normalize("  01.5 "))
        assertEquals("0.001", MeasurementInput.normalize("00.001"))
    }

    @Test
    fun keepsTrailingZerosAsEntered() {
        assertEquals("1.500", MeasurementInput.normalize("1.500"))
    }

    @Test
    fun rejectsNonPositiveValues() {
        assertNull(MeasurementInput.normalize("0"))
        assertNull(MeasurementInput.normalize("0.000"))
        assertNull(MeasurementInput.normalize("-1.2"))
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(MeasurementInput.normalize(""))
        assertNull(MeasurementInput.normalize("."))
        assertNull(MeasurementInput.normalize(".5"))
        assertNull(MeasurementInput.normalize("1."))
        assertNull(MeasurementInput.normalize("1.23456"))
        assertNull(MeasurementInput.normalize("1234567"))
        assertNull(MeasurementInput.normalize("1,234,5"))
        assertNull(MeasurementInput.normalize("width"))
    }
}
