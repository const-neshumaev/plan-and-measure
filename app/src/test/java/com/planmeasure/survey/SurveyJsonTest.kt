package com.planmeasure.survey

import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.units.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyJsonTest {

    @Test
    fun roundTripsFullDocument() {
        val survey = SurveyDocument(
            id = ID,
            name = "Living room",
            unit = LengthUnit.CENTIMETERS,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            photo = SurveyPhoto(
                width = 2048,
                height = 1536,
                source = SurveyPhotoSource.CAMERA,
            ),
            segments = listOf(
                SurveySegment(
                    id = SEGMENT_ID,
                    start = SurveyPoint(10.0, 20.0),
                    end = SurveyPoint(300.0, 20.0),
                    dimension = SurveyDimension(
                        valueMm = "3200",
                        source = MeasurementSource.RANGFINDER,
                        updatedAt = 1_500L,
                        comment = "Window wall",
                    ),
                ),
            ),
        )

        val decoded = SurveyJson.decode(SurveyJson.encode(survey))

        assertEquals(survey, decoded)
        assertEquals(1, decoded.segmentCount)
        assertEquals("Window wall", decoded.segments.single().dimension?.comment)
    }

    @Test
    fun readsDocumentWithoutOptionalFields() {
        val text = """
            {
              "id": "$ID",
              "name": "Photo",
              "createdAt": 1000
            }
        """.trimIndent()

        val decoded = SurveyJson.decode(text)

        assertEquals(ID, decoded.id)
        assertEquals(LengthUnit.METERS, decoded.unit)
        assertEquals(null, decoded.photo)
        assertTrue(decoded.segments.isEmpty())
        assertEquals(SURVEY_SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun ignoresUnknownFields() {
        val text = """
            {
              "schemaVersion": 1,
              "id": "$ID",
              "name": "Photo",
              "createdAt": 1000,
              "futureField": "ignored"
            }
        """.trimIndent()

        assertEquals("Photo", SurveyJson.decode(text).name)
    }

    private companion object {
        const val ID = "00000000-0000-0000-0000-000000000001"
        const val SEGMENT_ID = "00000000-0000-0000-0000-000000000002"
    }
}
