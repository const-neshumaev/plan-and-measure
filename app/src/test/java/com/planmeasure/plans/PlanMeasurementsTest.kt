package com.planmeasure.plans

import com.planmeasure.measurements.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanMeasurementsTest {

    @Test
    fun rectangleAreaAndPerimeterUseTheScale() {
        val plan = rectanglePlan()

        val measurement = requireNotNull(
            PlanMeasurements.measureContour(plan, "outline", subtractHoles = true),
        )

        assertEquals(12_000_000.0, measurement.grossAreaSquareMillimeters, TOLERANCE)
        assertEquals(12_000_000.0, measurement.netAreaSquareMillimeters, TOLERANCE)
        assertEquals(14_000.0, measurement.perimeterMillimeters, TOLERANCE)
        assertEquals(4, measurement.edgeCount)
        assertEquals(0, measurement.measuredEdges)
        assertEquals(0, measurement.mismatchedEdges)
        assertTrue(measurement.holes.isEmpty())
    }

    @Test
    fun nestedOutlineIsSubtractedAsAHole() {
        val plan = nestedPlan()

        val measurement = requireNotNull(
            PlanMeasurements.measureContour(plan, "outer", subtractHoles = true),
        )

        assertEquals(80_000_000.0, measurement.grossAreaSquareMillimeters, TOLERANCE)
        assertEquals(1, measurement.holes.size)
        assertEquals("inner", measurement.holes.single().contourId)
        assertEquals(4_000_000.0, measurement.holes.single().areaSquareMillimeters, TOLERANCE)
        assertEquals(76_000_000.0, measurement.netAreaSquareMillimeters, TOLERANCE)
        assertEquals(36_000.0, measurement.perimeterMillimeters, TOLERANCE)
    }

    @Test
    fun holesCanBeLeftOut() {
        val plan = nestedPlan()

        val measurement = requireNotNull(
            PlanMeasurements.measureContour(plan, "outer", subtractHoles = false),
        )

        assertEquals(80_000_000.0, measurement.netAreaSquareMillimeters, TOLERANCE)
        assertEquals(1, measurement.holes.size)
    }

    @Test
    fun aRectangleDrawnAsTwoChainsIsMeasuredAsOneLoop() {
        val plan = twoChainRectanglePlan()

        // Neither chain is closed on its own, which is why the old contour-based area could not
        // find this rectangle. The cycle between the chains is the shape the user sees.
        assertNull(PlanMeasurements.measureContour(plan, "top-and-sides", subtractHoles = true))
        assertNull(PlanMeasurements.measureContour(plan, "bottom", subtractHoles = true))

        val loop = requireNotNull(PlanMeasurements.loopAt(plan, PlanPoint(2.0, 1.5)))
        assertEquals(5, loop.edgeIds.size)

        val measurement = requireNotNull(
            PlanMeasurements.measureArea(plan, loop.edgeIds, subtractHoles = true),
        )
        assertEquals(12_000_000.0, measurement.grossAreaSquareMillimeters, TOLERANCE)
        assertEquals(12_000_000.0, measurement.netAreaSquareMillimeters, TOLERANCE)
        assertEquals(14_000.0, measurement.perimeterMillimeters, TOLERANCE)
        assertTrue(measurement.holes.isEmpty())

        val calculation = PlanCalculation(
            id = "area",
            name = "Area",
            kind = PlanCalculationKind.AREA,
            edgeIds = loop.edgeIds,
            createdAt = 1L,
        )
        assertTrue(PlanMeasurements.evaluate(plan, calculation).valid)
    }

    @Test
    fun edgeBasedAreaSubtractsANestedLoop() {
        val plan = nestedPlan()
        val outerLoop = requireNotNull(PlanMeasurements.loopAt(plan, PlanPoint(1.0, 1.0)))

        val measurement = requireNotNull(
            PlanMeasurements.measureArea(plan, outerLoop.edgeIds, subtractHoles = true),
        )

        assertEquals(80_000_000.0, measurement.grossAreaSquareMillimeters, TOLERANCE)
        assertEquals(1, measurement.holes.size)
        assertEquals(76_000_000.0, measurement.netAreaSquareMillimeters, TOLERANCE)

        val windowLoop = requireNotNull(PlanMeasurements.loopAt(plan, PlanPoint(3.0, 3.0)))
        val window = requireNotNull(
            PlanMeasurements.measureArea(plan, windowLoop.edgeIds, subtractHoles = true),
        )
        assertEquals(4_000_000.0, window.netAreaSquareMillimeters, TOLERANCE)
        assertTrue(window.holes.isEmpty())
    }

    @Test
    fun tapInsideNestedOutlineSelectsTheSmallestOne() {
        val plan = nestedPlan()

        assertEquals("inner", PlanMeasurements.contourAt(plan, PlanPoint(3.0, 3.0))?.id)
        assertEquals("outer", PlanMeasurements.contourAt(plan, PlanPoint(1.0, 1.0))?.id)
        assertNull(PlanMeasurements.contourAt(plan, PlanPoint(-1.0, -1.0)))
    }

    @Test
    fun anOutlineTouchingTheOuterOneIsNotAHole() {
        val plan = nestedPlan(innerLeft = 0.0, innerTop = 2.0)

        val measurement = requireNotNull(
            PlanMeasurements.measureContour(plan, "outer", subtractHoles = true),
        )

        assertTrue(measurement.holes.isEmpty())
        assertEquals(80_000_000.0, measurement.netAreaSquareMillimeters, TOLERANCE)
    }

    @Test
    fun openOutlineHasNoArea() {
        val plan = rectanglePlan().let { document ->
            val first = document.contours.single()
            document.copy(contours = listOf(first.copy(edges = first.edges.dropLast(1))))
        }

        assertNull(PlanMeasurements.contourPoints(plan, "outline"))
        assertNull(PlanMeasurements.measureContour(plan, "outline", subtractHoles = true))
    }

    @Test
    fun selfIntersectingOutlineHasNoArea() {
        val plan = selfIntersectingPlan()

        assertNull(PlanMeasurements.contourPoints(plan, "bow"))
        assertNull(PlanMeasurements.measureContour(plan, "bow", subtractHoles = true))
    }

    @Test
    fun selectedEdgesAddUpTheirLength() {
        val plan = rectanglePlan()

        val measurement = requireNotNull(
            PlanMeasurements.measureLength(plan, listOf("bottom", "right")),
        )

        assertEquals(7_000.0, measurement.lengthMillimeters, TOLERANCE)
        assertEquals(2, measurement.edgeCount)
    }

    @Test
    fun missingEdgeMakesTheLengthUnavailable() {
        val plan = rectanglePlan()

        assertNull(PlanMeasurements.measureLength(plan, listOf("bottom", "missing")))
    }

    @Test
    fun mismatchedDimensionIsReported() {
        val plan = rectanglePlan().let { document ->
            document.copy(
                contours = document.contours.map { contour ->
                    contour.copy(
                        edges = contour.edges.map { edge ->
                            if (edge.id == "bottom") {
                                edge.copy(
                                    dimension = PlanDimension(
                                        valueMm = "5000",
                                        source = MeasurementSource.MANUAL,
                                        updatedAt = 1L,
                                    ),
                                )
                            } else {
                                edge
                            }
                        },
                    )
                },
            )
        }

        val measurement = requireNotNull(
            PlanMeasurements.measureContour(plan, "outline", subtractHoles = true),
        )

        assertEquals(1, measurement.measuredEdges)
        assertEquals(1, measurement.mismatchedEdges)
    }

    @Test
    fun evaluationKeepsTheCalculationValidBeforeAndAfterEdits() {
        val plan = rectanglePlan()
        val area = PlanCalculation(
            id = "area",
            name = "Area",
            kind = PlanCalculationKind.AREA,
            contourId = "outline",
            createdAt = 1L,
        )
        val length = PlanCalculation(
            id = "length",
            name = "Length",
            kind = PlanCalculationKind.LENGTH,
            edgeIds = listOf("bottom", "right"),
            createdAt = 1L,
        )
        val missing = PlanCalculation(
            id = "missing",
            name = "Missing",
            kind = PlanCalculationKind.LENGTH,
            edgeIds = listOf("missing"),
            createdAt = 1L,
        )

        val areaResult = PlanMeasurements.evaluate(plan, area)
        assertTrue(areaResult.valid)
        assertNotNull(areaResult.contour)
        assertNull(areaResult.length)

        val lengthResult = PlanMeasurements.evaluate(plan, length)
        assertTrue(lengthResult.valid)
        assertEquals(7_000.0, lengthResult.length!!.lengthMillimeters, TOLERANCE)

        assertFalse(PlanMeasurements.evaluate(plan, missing).valid)
    }

    @Test
    fun aPlanWithoutScaleHasNoRealMeasurements() {
        val plan = rectanglePlan().copy(mmPerUnit = null)

        assertNull(PlanMeasurements.measureContour(plan, "outline", subtractHoles = true))
        assertNull(PlanMeasurements.measureLength(plan, listOf("bottom")))
    }

    private fun rectanglePlan(): PlanDocument {
        val nodes = listOf(
            PlanNode("a", 0.0, 0.0),
            PlanNode("b", 4.0, 0.0),
            PlanNode("c", 4.0, 3.0),
            PlanNode("d", 0.0, 3.0),
        )
        val edges = listOf(
            PlanEdge("bottom", "a", "b"),
            PlanEdge("right", "b", "c"),
            PlanEdge("top", "c", "d"),
            PlanEdge("left", "d", "a"),
        )
        return PlanDocument(
            id = "plan",
            name = "Plan",
            createdAt = 1L,
            mmPerUnit = 1000.0,
            nodes = nodes,
            contours = listOf(PlanContour("outline", edges)),
        )
    }

    private fun nestedPlan(innerLeft: Double = 2.0, innerTop: Double = 2.0): PlanDocument {
        val outer = rectanglePlan().let { document ->
            document.copy(
                nodes = listOf(
                    PlanNode("a", 0.0, 0.0),
                    PlanNode("b", 10.0, 0.0),
                    PlanNode("c", 10.0, 8.0),
                    PlanNode("d", 0.0, 8.0),
                ),
                contours = listOf(
                    PlanContour(
                        "outer",
                        listOf(
                            PlanEdge("bottom", "a", "b"),
                            PlanEdge("right", "b", "c"),
                            PlanEdge("top", "c", "d"),
                            PlanEdge("left", "d", "a"),
                        ),
                    ),
                ),
            )
        }
        val innerNodes = listOf(
            PlanNode("e", innerLeft, innerTop),
            PlanNode("f", innerLeft + 2.0, innerTop),
            PlanNode("g", innerLeft + 2.0, innerTop + 2.0),
            PlanNode("h", innerLeft, innerTop + 2.0),
        )
        val innerEdges = listOf(
            PlanEdge("inner-bottom", "e", "f"),
            PlanEdge("inner-right", "f", "g"),
            PlanEdge("inner-top", "g", "h"),
            PlanEdge("inner-left", "h", "e"),
        )
        return outer.copy(
            nodes = outer.nodes + innerNodes,
            contours = outer.contours + PlanContour("inner", innerEdges),
        )
    }

    private fun twoChainRectanglePlan(): PlanDocument {
        val nodes = listOf(
            PlanNode("a", 0.0, 0.0),
            PlanNode("b", 0.0, 3.0),
            PlanNode("c", 4.0, 3.0),
            PlanNode("d", 4.0, 0.0),
            PlanNode("e", 2.0, 0.0),
        )
        return PlanDocument(
            id = "plan",
            name = "Plan",
            createdAt = 1L,
            mmPerUnit = 1000.0,
            nodes = nodes,
            contours = listOf(
                PlanContour(
                    "top-and-sides",
                    listOf(
                        PlanEdge("left", "a", "b"),
                        PlanEdge("top", "b", "c"),
                        PlanEdge("right", "c", "d"),
                    ),
                ),
                PlanContour(
                    "bottom",
                    listOf(
                        PlanEdge("bottom-left", "a", "e"),
                        PlanEdge("bottom-right", "e", "d"),
                    ),
                ),
            ),
        )
    }

    private fun selfIntersectingPlan(): PlanDocument {
        val nodes = listOf(
            PlanNode("a", 0.0, 0.0),
            PlanNode("b", 4.0, 4.0),
            PlanNode("c", 0.0, 4.0),
            PlanNode("d", 4.0, 0.0),
        )
        val edges = listOf(
            PlanEdge("e1", "a", "b"),
            PlanEdge("e2", "b", "c"),
            PlanEdge("e3", "c", "d"),
            PlanEdge("e4", "d", "a"),
        )
        return PlanDocument(
            id = "plan",
            name = "Plan",
            createdAt = 1L,
            mmPerUnit = 1000.0,
            nodes = nodes,
            contours = listOf(PlanContour("bow", edges)),
        )
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
