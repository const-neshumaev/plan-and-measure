package com.planmeasure.measurements

import com.planmeasure.units.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementListJsonTest {

    private val id = "00000000-0000-0000-0000-000000000001"

    private fun sample(): MeasurementList = MeasurementList(
        id = id,
        name = "Living room",
        unit = LengthUnit.CENTIMETERS,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        measurements = listOf(
            Measurement(
                id = "00000000-0000-0000-0000-000000000002",
                valueMm = "1763",
                comment = "Window width",
                createdAt = 1_500L,
                source = MeasurementSource.RANGFINDER,
            ),
        ),
    )

    @Test
    fun roundTripsDocument() {
        val decoded = MeasurementListJson.decode(MeasurementListJson.encode(sample()))
        assertEquals(sample(), decoded)
    }

    @Test
    fun ignoresUnknownFields() {
        val text = """
            {
              "schemaVersion": 2,
              "id": "$id",
              "name": "Room",
              "unit": "METERS",
              "createdAt": 10,
              "updatedAt": 20,
              "futureField": {"nested": true},
              "measurements": []
            }
        """.trimIndent()

        val decoded = MeasurementListJson.decode(text)
        assertEquals("Room", decoded.name)
        assertEquals(10L, decoded.createdAt)
        assertEquals(20L, decoded.updatedAt)
    }

    @Test
    fun fillsDefaultsForOptionalFields() {
        val text = """
            {
              "id": "$id",
              "name": "Room",
              "createdAt": 10,
              "measurements": [
                {
                  "id": "00000000-0000-0000-0000-000000000003",
                  "valueMm": "762",
                  "createdAt": 15,
                  "source": "MANUAL"
                }
              ]
            }
        """.trimIndent()

        val decoded = MeasurementListJson.decode(text)
        assertEquals(MEASUREMENT_LIST_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(LengthUnit.METERS, decoded.unit)
        assertEquals(decoded.createdAt, decoded.updatedAt)
        assertEquals("", decoded.measurements.single().comment)
    }
}
