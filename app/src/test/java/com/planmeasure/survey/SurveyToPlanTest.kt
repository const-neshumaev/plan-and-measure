package com.planmeasure.survey

import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.plans.PlanContent
import java.util.UUID
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyToPlanTest {

    private fun surveyOf(vararg segments: SurveySegment) = SurveyDocument(
        id = "survey",
        name = "Survey",
        createdAt = 1_000L,
        segments = segments.toList(),
    )

    private fun point(x: Double, y: Double) = SurveyPoint(x, y)

    private fun segment(
        start: SurveyPoint,
        end: SurveyPoint,
        dimension: SurveyDimension? = null,
    ) = SurveySegment(
        id = UUID.randomUUID().toString(),
        start = start,
        end = end,
        dimension = dimension,
    )

    private fun ids(): () -> String {
        var next = 0
        return { "id-${next++}" }
    }

    private fun assertHasNode(content: PlanContent, x: Double, y: Double) {
        assertTrue(
            "no node at ($x, $y) among ${content.nodes}",
            content.nodes.any { abs(it.x - x) < 1e-6 && abs(it.y - y) < 1e-6 },
        )
    }

    private fun triangle(
        a: SurveyPoint = point(100.0, 100.0),
        b: SurveyPoint = point(900.0, 100.0),
        c: SurveyPoint = point(500.0, 700.0),
    ) = surveyOf(segment(a, b), segment(b, c), segment(c, a))

    @Test
    fun sharedEndpointsBecomeOneNodeAndAClosedContour() {
        val content = SurveyToPlan.convert(triangle(), ids())!!

        assertEquals(3, content.nodes.size)
        assertEquals(1, content.contours.size)
        assertEquals(3, content.contours.single().edges.size)
        assertTrue(content.contours.single().isClosed)
    }

    @Test
    fun fitsDrawingIntoTheDefaultSheetAndCentresIt() {
        val content = SurveyToPlan.convert(triangle(), ids())!!

        assertHasNode(content, x = 20.0, y = 80.0)
        assertHasNode(content, x = 180.0, y = 80.0)
        assertHasNode(content, x = 100.0, y = 200.0)
    }

    @Test
    fun fitsADegenerateBoundingBoxOnOneAxis() {
        val content = SurveyToPlan.convert(
            surveyOf(segment(point(0.0, 0.0), point(0.0, 500.0))),
            ids(),
        )!!

        assertHasNode(content, x = 100.0, y = 20.0)
        assertHasNode(content, x = 100.0, y = 260.0)
    }

    @Test
    fun copiesDimensionLabelsWithoutAScale() {
        val dimension = SurveyDimension(
            valueMm = "2500",
            source = MeasurementSource.RANGFINDER,
            updatedAt = 5_000L,
            comment = "window",
        )

        val content = SurveyToPlan.convert(
            surveyOf(segment(point(0.0, 0.0), point(500.0, 0.0), dimension)),
            ids(),
        )!!
        val edge = content.contours.single().edges.single()

        assertEquals("2500", edge.dimension?.valueMm)
        assertEquals(MeasurementSource.RANGFINDER, edge.dimension?.source)
        assertEquals(5_000L, edge.dimension?.updatedAt)
    }

    @Test
    fun disconnectedLinesBecomeSeparateContours() {
        val content = SurveyToPlan.convert(
            surveyOf(
                segment(point(0.0, 0.0), point(100.0, 0.0)),
                segment(point(0.0, 200.0), point(100.0, 200.0)),
            ),
            ids(),
        )!!

        assertEquals(2, content.contours.size)
    }

    @Test
    fun aClosedContourIsNotExtendedByANewLine() {
        val a = point(0.0, 0.0)
        val b = point(100.0, 0.0)
        val c = point(0.0, 100.0)

        val content = SurveyToPlan.convert(
            surveyOf(
                segment(a, b),
                segment(b, c),
                segment(c, a),
                segment(a, point(50.0, 50.0)),
            ),
            ids(),
        )!!

        assertEquals(2, content.contours.size)
        assertTrue(content.contours.first().isClosed)
        assertEquals(1, content.contours.last().edges.size)
    }

    @Test
    fun returnsNullWhenNothingIsDrawn() {
        assertNull(SurveyToPlan.convert(surveyOf()))
        assertNull(
            SurveyToPlan.convert(
                surveyOf(segment(point(10.0, 10.0), point(10.0, 10.0))),
            ),
        )
    }
}
