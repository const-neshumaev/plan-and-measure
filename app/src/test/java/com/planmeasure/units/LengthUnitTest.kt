package com.planmeasure.units

import com.planmeasure.protocol.DistanceUnit
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LengthUnitTest {

    @Test
    fun mapsProtocolUnits() {
        assertEquals(LengthUnit.METERS, DistanceUnit.METERS.toLengthUnit())
        assertEquals(LengthUnit.FEET, DistanceUnit.FEET.toLengthUnit())
        assertEquals(LengthUnit.INCHES, DistanceUnit.INCHES.toLengthUnit())
        assertEquals(LengthUnit.MILLIMETERS, DistanceUnit.MILLIMETERS.toLengthUnit())
        assertNull(DistanceUnit.UNKNOWN.toLengthUnit())
    }

    @Test
    fun convertsEnteredValuesToCanonicalMillimeters() {
        assertEquals("1763", Length.toMillimeters(BigDecimal("1.763"), LengthUnit.METERS))
        assertEquals("1234.567", Length.toMillimeters(BigDecimal("123.4567"), LengthUnit.CENTIMETERS))
        assertEquals("1234.5", Length.toMillimeters(BigDecimal("1234.5"), LengthUnit.MILLIMETERS))
        assertEquals("304.8", Length.toMillimeters(BigDecimal.ONE, LengthUnit.FEET))
        assertEquals("25.4", Length.toMillimeters(BigDecimal.ONE, LengthUnit.INCHES))
    }

    @Test
    fun keepsFractionalInchValuesExactly() {
        // 1/16 inch is 1.5875 mm; a fraction must survive the trip to inches as well.
        assertEquals("1.5875", Length.toMillimeters(BigDecimal("0.0625"), LengthUnit.INCHES))
        assertEquals("0.0625", Length.format("1.5875", LengthUnit.INCHES))
        // 32 5/8 in, a confirmed frame from the protocol log.
        assertEquals("828.675", Length.toMillimeters(BigDecimal("32.625"), LengthUnit.INCHES))
        assertEquals("32.625", Length.format("828.675", LengthUnit.INCHES))
    }

    @Test
    fun formatsWithThePrecisionOfTheUnit() {
        // 1.5 m stored as 1500 mm is shown without artificial trailing zeros.
        assertEquals("1.5", Length.format("1500", LengthUnit.METERS))
        assertEquals("150", Length.format("1500", LengthUnit.CENTIMETERS))
        assertEquals("1500", Length.format("1500", LengthUnit.MILLIMETERS))
        assertEquals("4.9213", Length.format("1500", LengthUnit.FEET))
        assertEquals("59.0551", Length.format("1500", LengthUnit.INCHES))
    }

    @Test
    fun formatsDeviceValuesBackToTheirUnit() {
        assertEquals("3.69", Length.format("3690", LengthUnit.METERS))
        assertEquals("11.961", Length.format("3645.7128", LengthUnit.FEET))
        assertEquals("159.879", Length.format("4060.9266", LengthUnit.INCHES))
        assertEquals("1234.6", Length.format("1234.5678", LengthUnit.MILLIMETERS))
        assertEquals("123.5", Length.format("1234.5678", LengthUnit.CENTIMETERS))
    }

    @Test
    fun returnsDamagedTextAsIs() {
        assertEquals("not a number", Length.format("not a number", LengthUnit.METERS))
    }
}
