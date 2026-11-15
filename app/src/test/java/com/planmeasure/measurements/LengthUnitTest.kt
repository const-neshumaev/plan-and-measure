package com.planmeasure.measurements

import com.planmeasure.protocol.DistanceUnit
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
}
