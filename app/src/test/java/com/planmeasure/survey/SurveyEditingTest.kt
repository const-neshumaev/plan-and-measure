package com.planmeasure.survey

import com.planmeasure.measurements.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyEditingTest {

    private val document = SurveyDocument(
        id = SURVEY_ID,
        name = "Photo",
        createdAt = 1_000L,
        photo = SurveyPhoto(width = 4000, height = 3000),
    )

    @Test
    fun addsSegmentSnappedToExistingEndpoint() {
        val first = SurveyEditing.addSegment(
            document = document,
            from = SurveyPoint(100.0, 100.0),
            to = SurveyPoint(400.0, 100.0),
            snapRadius = 10.0,
            id = SEGMENT_A,
        )
        val second = SurveyEditing.addSegment(
            document = first,
            from = SurveyPoint(402.0, 102.0),
            to = SurveyPoint(402.0, 300.0),
            snapRadius = 10.0,
            id = SEGMENT_B,
        )

        assertEquals(2, second.segmentCount)
        assertEquals(400.0, second.segments[1].start.x, 0.001)
        assertEquals(100.0, second.segments[1].start.y, 0.001)
    }

    @Test
    fun clampsSegmentToTheSheet() {
        val updated = SurveyEditing.addSegment(
            document = document,
            from = SurveyPoint(-50.0, -50.0),
            to = SurveyPoint(2000.0, 2000.0),
            snapRadius = 0.0,
            id = SEGMENT_A,
        )

        val segment = updated.segments.single()
        assertEquals(0.0, segment.start.x, 0.001)
        assertEquals(0.0, segment.start.y, 0.001)
        assertEquals(1000.0, segment.end.x, 0.001)
        assertEquals(750.0, segment.end.y, 0.001)
    }

    @Test
    fun ignoresTapSizedDrag() {
        val updated = SurveyEditing.addSegment(
            document = document,
            from = SurveyPoint(100.0, 100.0),
            to = SurveyPoint(101.0, 101.0),
            snapRadius = 0.0,
            id = SEGMENT_A,
        )
        assertSame(document, updated)
    }

    @Test
    fun doesNotDrawWithoutPhoto() {
        val withoutPhoto = document.copy(photo = null)
        val updated = SurveyEditing.addSegment(
            document = withoutPhoto,
            from = SurveyPoint(100.0, 100.0),
            to = SurveyPoint(400.0, 100.0),
            snapRadius = 0.0,
            id = SEGMENT_A,
        )
        assertSame(withoutPhoto, updated)
    }

    @Test
    fun movesCoincidentEndpointsTogether() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 400.0, 100.0),
            segment(SEGMENT_B, 400.0, 100.0, 400.0, 300.0),
        )

        val moved = SurveyEditing.moveEndpoint(
            document = document,
            segmentId = SEGMENT_A,
            endpoint = SurveyEndpoint.END,
            to = SurveyPoint(500.0, 200.0),
            snapRadius = 0.0,
        )

        assertEquals(500.0, moved.segments[0].end.x, 0.001)
        assertEquals(200.0, moved.segments[0].end.y, 0.001)
        assertEquals(500.0, moved.segments[1].start.x, 0.001)
        assertEquals(200.0, moved.segments[1].start.y, 0.001)
        assertEquals(400.0, moved.segments[1].end.x, 0.001)
    }

    @Test
    fun snapsMovedEndpointToAFreeEndpoint() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 300.0, 100.0),
            segment(SEGMENT_B, 600.0, 100.0, 600.0, 300.0),
        )

        val moved = SurveyEditing.moveEndpoint(
            document = document,
            segmentId = SEGMENT_A,
            endpoint = SurveyEndpoint.END,
            to = SurveyPoint(602.0, 102.0),
            snapRadius = 10.0,
        )

        assertEquals(600.0, moved.segments[0].end.x, 0.001)
        assertEquals(100.0, moved.segments[0].end.y, 0.001)
    }

    @Test
    fun movedEndpointDoesNotSnapBackToItsOwnCorner() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 300.0, 100.0),
        )

        val moved = SurveyEditing.moveEndpoint(
            document = document,
            segmentId = SEGMENT_A,
            endpoint = SurveyEndpoint.END,
            to = SurveyPoint(304.0, 104.0),
            snapRadius = 10.0,
        )

        assertEquals(304.0, moved.segments[0].end.x, 0.001)
        assertEquals(104.0, moved.segments[0].end.y, 0.001)
    }

    @Test
    fun clampsMovedEndpointToTheSheet() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 300.0, 100.0),
        )

        val moved = SurveyEditing.moveEndpoint(
            document = document,
            segmentId = SEGMENT_A,
            endpoint = SurveyEndpoint.END,
            to = SurveyPoint(-50.0, 900.0),
            snapRadius = 0.0,
        )

        assertEquals(0.0, moved.segments[0].end.x, 0.001)
        assertEquals(750.0, moved.segments[0].end.y, 0.001)
    }

    @Test
    fun slidesWholeSegmentKeepingDirectionAndDimension() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 300.0, 100.0)
                .copy(dimension = SurveyDimension("3200", MeasurementSource.MANUAL, 1_500L)),
        )

        val moved = SurveyEditing.moveSegment(
            document = document,
            segmentId = SEGMENT_A,
            delta = SurveyPoint(50.0, 25.0),
        )

        assertEquals(150.0, moved.segments[0].start.x, 0.001)
        assertEquals(125.0, moved.segments[0].start.y, 0.001)
        assertEquals(350.0, moved.segments[0].end.x, 0.001)
        assertEquals(125.0, moved.segments[0].end.y, 0.001)
        assertEquals("3200", moved.segments[0].dimension?.valueMm)
    }

    @Test
    fun slidingSegmentStretchesNeighbourAtSharedCorner() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 300.0, 100.0),
            segment(SEGMENT_B, 300.0, 100.0, 300.0, 300.0),
        )

        val moved = SurveyEditing.moveSegment(
            document = document,
            segmentId = SEGMENT_A,
            delta = SurveyPoint(0.0, 50.0),
        )

        assertEquals(150.0, moved.segments[0].start.y, 0.001)
        assertEquals(150.0, moved.segments[0].end.y, 0.001)
        assertEquals(150.0, moved.segments[1].start.y, 0.001)
        assertEquals(300.0, moved.segments[1].end.y, 0.001)
    }

    @Test
    fun slidingSegmentClampsToTheSheet() {
        val document = documentWith(
            segment(SEGMENT_A, 100.0, 100.0, 300.0, 100.0),
        )

        val moved = SurveyEditing.moveSegment(
            document = document,
            segmentId = SEGMENT_A,
            delta = SurveyPoint(5000.0, 0.0),
        )

        assertEquals(800.0, moved.segments[0].start.x, 0.001)
        assertEquals(1000.0, moved.segments[0].end.x, 0.001)
    }

    @Test
    fun removesSegmentAndDimensionWithIt() {
        val withDimension = SurveyEditing.addSegment(
            document,
            SurveyPoint(100.0, 100.0),
            SurveyPoint(400.0, 100.0),
            0.0,
            SEGMENT_A,
        ).let { added ->
            SurveyEditing.setDimension(
                added,
                SEGMENT_A,
                SurveyDimension("3200", MeasurementSource.MANUAL, 1_500L),
            )
        }

        assertEquals(1, withDimension.segmentCount)
        assertNotNull(withDimension.segments.single().dimension)

        val removed = SurveyEditing.removeSegment(withDimension, SEGMENT_A)
        assertTrue(removed.segments.isEmpty())
    }

    @Test
    fun removesDimensionButKeepsSegment() {
        val withDimension = SurveyEditing.addSegment(
            document,
            SurveyPoint(100.0, 100.0),
            SurveyPoint(400.0, 100.0),
            0.0,
            SEGMENT_A,
        ).let { added ->
            SurveyEditing.setDimension(
                added,
                SEGMENT_A,
                SurveyDimension("3200", MeasurementSource.MANUAL, 1_500L),
            )
        }

        val cleared = SurveyEditing.removeDimension(withDimension, SEGMENT_A)

        assertEquals(1, cleared.segmentCount)
        assertNull(cleared.segments.single().dimension)
    }

    @Test
    fun setsPhotoOnlyWhileSketchIsEmpty() {
        val first = SurveyPhoto(2048, 1536, SurveyPhotoSource.GALLERY)
        val second = SurveyPhoto(1536, 2048, SurveyPhotoSource.CAMERA)

        val attached = SurveyEditing.setPhoto(document, first)
        assertEquals(first, attached.photo)

        val replaced = SurveyEditing.setPhoto(attached, second)
        assertEquals(second, replaced.photo)

        val drawn = SurveyEditing.addSegment(
            attached,
            SurveyPoint(100.0, 100.0),
            SurveyPoint(400.0, 100.0),
            0.0,
            SEGMENT_A,
        )
        assertSame(drawn, SurveyEditing.setPhoto(drawn, second))
    }

    @Test
    fun clearsSketchAndKeepsPhoto() {
        val drawn = SurveyEditing.addSegment(
            document,
            SurveyPoint(100.0, 100.0),
            SurveyPoint(400.0, 100.0),
            0.0,
            SEGMENT_A,
        )

        val cleared = SurveyEditing.clearSketch(drawn)

        assertTrue(cleared.segments.isEmpty())
        assertEquals(document.photo, cleared.photo)
    }

    private fun documentWith(vararg segments: SurveySegment): SurveyDocument =
        document.copy(segments = segments.toList())

    private fun segment(
        id: String,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ) = SurveySegment(
        id = id,
        start = SurveyPoint(startX, startY),
        end = SurveyPoint(endX, endY),
    )

    private companion object {
        const val SURVEY_ID = "00000000-0000-0000-0000-000000000001"
        const val SEGMENT_A = "00000000-0000-0000-0000-000000000010"
        const val SEGMENT_B = "00000000-0000-0000-0000-000000000011"
    }
}
