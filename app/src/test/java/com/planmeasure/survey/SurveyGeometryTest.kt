package com.planmeasure.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyGeometryTest {

    @Test
    fun sheetKeepsPhotoAspect() {
        val landscape = SurveyGeometry.sheetFor(SurveyPhoto(4000, 3000))
        assertEquals(1000.0, landscape.width, 0.001)
        assertEquals(750.0, landscape.height, 0.001)

        val portrait = SurveyGeometry.sheetFor(SurveyPhoto(3000, 4000))
        assertEquals(1000.0, portrait.width, 0.001)
        assertEquals(1333.333, portrait.height, 0.001)
    }

    @Test
    fun sheetWithoutPhotoFallsBackToFourByThree() {
        val sheet = SurveyGeometry.sheetFor(null)
        assertEquals(1000.0, sheet.width, 0.001)
        assertEquals(750.0, sheet.height, 0.001)
    }

    @Test
    fun ignoresBrokenPhotoSize() {
        val sheet = SurveyGeometry.sheetFor(SurveyPhoto(width = 0, height = 0))
        assertEquals(750.0, sheet.height, 0.001)
    }

    @Test
    fun findsNearestSegmentWithinTolerance() {
        val segments = listOf(
            SurveySegment(ID_A, SurveyPoint(0.0, 0.0), SurveyPoint(100.0, 0.0)),
            SurveySegment(ID_B, SurveyPoint(0.0, 100.0), SurveyPoint(100.0, 100.0)),
        )

        assertEquals(ID_A, SurveyGeometry.nearestSegment(segments, SurveyPoint(50.0, 4.0), 10.0)?.id)
        assertEquals(ID_B, SurveyGeometry.nearestSegment(segments, SurveyPoint(50.0, 96.0), 10.0)?.id)
        assertNull(SurveyGeometry.nearestSegment(segments, SurveyPoint(50.0, 50.0), 10.0))
    }

    @Test
    fun measuresDistanceToSegmentEnds() {
        val segment = SurveySegment(ID_A, SurveyPoint(0.0, 0.0), SurveyPoint(100.0, 0.0))
        assertEquals(0.0, SurveyGeometry.distanceToSegment(segment, SurveyPoint(50.0, 0.0)), 0.001)
        assertEquals(10.0, SurveyGeometry.distanceToSegment(segment, SurveyPoint(110.0, 0.0)), 0.001)
    }

    @Test
    fun findsTheGrabbedEndpoint() {
        val segments = listOf(
            SurveySegment(ID_A, SurveyPoint(0.0, 0.0), SurveyPoint(100.0, 0.0)),
            SurveySegment(ID_B, SurveyPoint(100.0, 0.0), SurveyPoint(100.0, 100.0)),
        )

        val hit = SurveyGeometry.nearestSegmentEndpoint(segments, SurveyPoint(101.0, 1.0), 10.0)

        assertEquals(ID_A, hit?.segmentId)
        assertEquals(SurveyEndpoint.END, hit?.endpoint)
        assertEquals(100.0, hit?.point?.x ?: -1.0, 0.001)
    }

    @Test
    fun findsNearestPointWithinRadius() {
        val points = listOf(SurveyPoint(0.0, 0.0), SurveyPoint(100.0, 0.0))

        assertEquals(
            SurveyPoint(100.0, 0.0),
            SurveyGeometry.nearestPoint(points, SurveyPoint(105.0, 0.0), 10.0),
        )
        assertNull(SurveyGeometry.nearestPoint(points, SurveyPoint(50.0, 0.0), 10.0))
    }

    @Test
    fun snapsOnlyToNearbyEndpoints() {
        val segments = listOf(
            SurveySegment(ID_A, SurveyPoint(0.0, 0.0), SurveyPoint(100.0, 0.0)),
        )

        val snapped = SurveyGeometry.snapPoint(segments, SurveyPoint(101.0, 1.0), radius = 5.0)
        assertEquals(100.0, snapped.x, 0.001)
        assertEquals(0.0, snapped.y, 0.001)

        val free = SurveyGeometry.snapPoint(segments, SurveyPoint(150.0, 50.0), radius = 5.0)
        assertEquals(150.0, free.x, 0.001)
        assertEquals(50.0, free.y, 0.001)
    }

    @Test
    fun excludesTheSegmentItselfWhenAsked() {
        val segments = listOf(
            SurveySegment(ID_A, SurveyPoint(0.0, 0.0), SurveyPoint(100.0, 0.0)),
        )

        assertNull(SurveyGeometry.nearestEndpoint(segments, SurveyPoint(100.0, 0.0), 5.0, ID_A))
    }

    @Test
    fun clampsPointsToThePhotoSheet() {
        val sheet = SurveySheet(1000.0, 750.0)
        val clamped = SurveyGeometry.clamp(SurveyPoint(-10.0, 900.0), sheet)
        assertEquals(0.0, clamped.x, 0.001)
        assertEquals(750.0, clamped.y, 0.001)
    }

    @Test
    fun rejectsTapSizedSegments() {
        assertFalse(
            SurveyGeometry.canAddSegment(
                SurveyPoint(0.0, 0.0),
                SurveyPoint(1.0, 1.0),
            ),
        )
        assertTrue(
            SurveyGeometry.canAddSegment(
                SurveyPoint(0.0, 0.0),
                SurveyPoint(50.0, 0.0),
            ),
        )
    }

    private companion object {
        const val ID_A = "a"
        const val ID_B = "b"
    }
}
