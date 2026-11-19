package com.planmeasure.plans

import com.planmeasure.measurements.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanScaleTest {

    @Test
    fun firstDimensionSetsTheScaleAndMovesNothing() {
        val plan = rectangle()
        val bottom = plan.edges.first()

        val scaled = PlanEditing.setDimension(plan, bottom.id, dimension("6000", 2_000L))

        assertEquals(1000.0, scaled.mmPerUnit ?: 0.0, 1e-9)
        assertEquals(bottom.id, scaled.scaleEdgeId)
        assertEquals(plan.nodes, scaled.nodes)
        assertFalse(PlanScale.isMismatched(scaled, bottom.id))
    }

    @Test
    fun secondDimensionResizesTheRoomKeepingAngles() {
        val plan = rectangle()
        val (bottom, right) = plan.edges

        val scaled = PlanEditing.setDimension(plan, bottom.id, dimension("6000", 2_000L))
        val fitted = PlanEditing.setDimension(scaled, right.id, dimension("9000", 3_000L))

        // Opposite sides of a rectangle are equal, so the free walls follow the measured ones and
        // the figure stays a rectangle: 6 x 9 grid units for 6 x 9 meters.
        assertEquals(6000.0, drawn(fitted, "bottom") ?: 0.0, MM_TOLERANCE)
        assertEquals(9000.0, drawn(fitted, "right") ?: 0.0, MM_TOLERANCE)
        assertEquals(6000.0, drawn(fitted, "top") ?: 0.0, MM_TOLERANCE)
        assertEquals(9000.0, drawn(fitted, "left") ?: 0.0, MM_TOLERANCE)
        assertPoint(PlanPoint(2.0, 2.0), positionOf(fitted, "a"))
        assertPoint(PlanPoint(8.0, 2.0), positionOf(fitted, "b"))
        assertPoint(PlanPoint(8.0, 11.0), positionOf(fitted, "c"))
        assertPoint(PlanPoint(2.0, 11.0), positionOf(fitted, "d"))
        assertDirectionsKept(plan, fitted)
        assertTrue(PlanScale.mismatchedEdgeIds(fitted).isEmpty())
    }

    @Test
    fun chainStretchesOnlyTheMeasuredWall() {
        val plan = chain()
        val (first, second) = plan.edges

        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))
        val fitted = PlanEditing.setDimension(scaled, second.id, dimension("3000", 3_000L))

        assertEquals(6000.0, drawn(fitted, "first") ?: 0.0, MM_TOLERANCE)
        assertEquals(3000.0, drawn(fitted, "second") ?: 0.0, MM_TOLERANCE)
        assertPoint(PlanPoint(2.0, 2.0), positionOf(fitted, "a"))
        assertPoint(PlanPoint(8.0, 2.0), positionOf(fitted, "b"))
        assertPoint(PlanPoint(8.0, 5.0), positionOf(fitted, "c"))
        assertDirectionsKept(plan, fitted)
    }

    @Test
    fun dimensionTheAnglesCannotHoldKeepsTheDrawing() {
        val plan = triangle()
        val (base, side) = plan.edges

        val scaled = PlanEditing.setDimension(plan, base.id, dimension("6000", 2_000L))
        val attempted = PlanEditing.setDimension(scaled, side.id, dimension("5000", 3_000L))

        // A triangle with fixed angles has one free length, so the second dimension can only stay a
        // label: the drawing is untouched and the wall is marked as disagreeing with it.
        assertEquals(scaled.nodes, attempted.nodes)
        assertEquals("5000", attempted.edges.first { it.id == side.id }.dimension?.valueMm)
        assertEquals(4000.0, drawn(attempted, "side") ?: 0.0, MM_TOLERANCE)
        assertFalse(PlanScale.isMismatched(attempted, base.id))
        assertTrue(PlanScale.isMismatched(attempted, side.id))
    }

    @Test
    fun oppositeSidesKeepTheFirstMeasurement() {
        val plan = rectangle()
        val bottom = plan.edges.first()
        val top = plan.edges.first { it.id == "top" }

        val scaled = PlanEditing.setDimension(plan, bottom.id, dimension("6000", 2_000L))
        val attempted = PlanEditing.setDimension(scaled, top.id, dimension("6200", 3_000L))

        assertEquals(scaled.nodes, attempted.nodes)
        assertEquals(6000.0, drawn(attempted, "top") ?: 0.0, MM_TOLERANCE)
        assertFalse(PlanScale.isMismatched(attempted, bottom.id))
        assertTrue(PlanScale.isMismatched(attempted, top.id))
    }

    @Test
    fun remeasuringTheScaleWallMovesTheScale() {
        val plan = chain()
        val (first, second) = plan.edges

        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))
        val fitted = PlanEditing.setDimension(scaled, second.id, dimension("3000", 3_000L))
        // The user corrects the wall everything else was derived from: it keeps its drawn length and
        // every other dimension is converted with the new ratio.
        val corrected = PlanEditing.setDimension(fitted, first.id, dimension("3000", 4_000L))

        assertEquals(500.0, corrected.mmPerUnit ?: 0.0, 1e-9)
        assertEquals(first.id, corrected.scaleEdgeId)
        assertEquals(3000.0, drawn(corrected, "second") ?: 0.0, MM_TOLERANCE)
        assertTrue(PlanScale.mismatchedEdgeIds(corrected).isEmpty())
    }

    @Test
    fun removingTheLastDimensionForgetsTheScale() {
        val plan = chain()
        val first = plan.edges.first()
        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))

        val cleared = PlanEditing.removeDimension(scaled, first.id)

        assertNull(cleared.mmPerUnit)
        assertNull(cleared.scaleEdgeId)
        assertEquals(scaled.nodes, cleared.nodes)
    }

    @Test
    fun removingTheScaleWallKeepsTheScale() {
        val plan = chain()
        val (first, second) = plan.edges
        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))
        val measured = PlanEditing.setDimension(scaled, second.id, dimension("3000", 3_000L))

        val cleared = PlanEditing.removeDimension(measured, first.id)

        assertEquals(1000.0, cleared.mmPerUnit ?: 0.0, 1e-9)
        assertNull(cleared.scaleEdgeId)
    }

    @Test
    fun splittingTheScaleWallKeepsTheScale() {
        val plan = chain()
        val (first, second) = plan.edges
        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))
        val measured = PlanEditing.setDimension(scaled, second.id, dimension("3000", 3_000L))

        val split = PlanEditing.splitEdge(measured, first.id, PlanPoint(5.0, 2.0), idFactory())

        assertEquals(1000.0, split.mmPerUnit ?: 0.0, 1e-9)
        assertNull(split.scaleEdgeId)
    }

    @Test
    fun reshapingAfterTheFitMarksTheDimension() {
        val plan = chain()
        val first = plan.edges.first()
        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))

        // Dimensions are applied when entered, not enforced while editing: the node follows the
        // finger and the wall is marked instead of snapping back.
        val moved = PlanEditing.moveNode(scaled, "b", PlanPoint(8.0, 4.0), magnetRadius = 0.0)

        assertTrue(PlanScale.isMismatched(moved, first.id))
        assertEquals("6000", moved.edges.first { it.id == first.id }.dimension?.valueMm)
    }

    @Test
    fun nextDimensionFitsTheWallsTheDragBroke() {
        val plan = chain()
        val (first, second) = plan.edges
        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))
        val dragged = PlanEditing.moveNode(scaled, "b", PlanPoint(8.0, 4.0), magnetRadius = 0.0)
        assertTrue(PlanScale.isMismatched(dragged, first.id))

        // Entering another dimension fits the drawing again, so a broken wall comes back to its
        // measured length without the user having to repair it by hand.
        val fitted = PlanEditing.setDimension(dragged, second.id, dimension("2000", 3_000L))

        assertEquals(6000.0, drawn(fitted, "first") ?: 0.0, MM_TOLERANCE)
        assertEquals(2000.0, drawn(fitted, "second") ?: 0.0, MM_TOLERANCE)
        assertTrue(PlanScale.mismatchedEdgeIds(fitted).isEmpty())
        assertDirectionsKept(dragged, fitted)
    }

    @Test
    fun fitGrowsTheSheet() {
        val plan = chain(world = PlanSize(width = 20.0, height = 20.0))
        val (first, second) = plan.edges

        val scaled = PlanEditing.setDimension(plan, first.id, dimension("6000", 2_000L))
        val fitted = PlanEditing.setDimension(scaled, second.id, dimension("30000", 3_000L))

        assertEquals(40.0, fitted.world.height, 1e-9)
        assertPoint(PlanPoint(8.0, 32.0), positionOf(fitted, "c"))
    }

    @Test
    fun unscaledPlanHasNoMismatch() {
        val plan = chain()

        assertNull(PlanScale.millimetersPerUnit(plan))
        assertNull(PlanScale.drawnMillimeters(plan, "first"))
        assertTrue(PlanScale.mismatchedEdgeIds(plan).isEmpty())
    }

    @Test
    fun dimensionStaysOnTheWallItWasEnteredFor() {
        val plan = rectangle()
        val bottom = plan.edges.first()
        val scaled = PlanEditing.setDimension(plan, bottom.id, dimension("6000", 2_000L))

        val fitted = PlanEditing.setDimension(
            scaled,
            plan.edges.first { it.id == "right" }.id,
            dimension("9000", 3_000L),
        )

        assertEquals("6000", fitted.edges.first { it.id == "bottom" }.dimension?.valueMm)
        assertEquals("9000", fitted.edges.first { it.id == "right" }.dimension?.valueMm)
    }

    private fun rectangle(): PlanDocument = plan(
        nodes = listOf(
            node("a", 2.0, 2.0),
            node("b", 8.0, 2.0),
            node("c", 8.0, 8.0),
            node("d", 2.0, 8.0),
        ),
        edges = listOf(
            edge("bottom", "a", "b"),
            edge("right", "b", "c"),
            edge("top", "c", "d"),
            edge("left", "d", "a"),
        ),
    )

    private fun chain(world: PlanSize = PlanSize()): PlanDocument = plan(
        nodes = listOf(
            node("a", 2.0, 2.0),
            node("b", 8.0, 2.0),
            node("c", 8.0, 6.0),
        ),
        edges = listOf(
            edge("first", "a", "b"),
            edge("second", "b", "c"),
        ),
        world = world,
    )

    private fun triangle(): PlanDocument = plan(
        nodes = listOf(
            node("a", 2.0, 2.0),
            node("b", 8.0, 2.0),
            node("c", 8.0, 6.0),
        ),
        edges = listOf(
            edge("base", "a", "b"),
            edge("side", "b", "c"),
            edge("slope", "c", "a"),
        ),
    )

    private fun plan(
        nodes: List<PlanNode>,
        edges: List<PlanEdge>,
        world: PlanSize = PlanSize(),
    ) = PlanDocument(
        id = PLAN_ID,
        name = "Room",
        createdAt = 1_000L,
        world = world,
        nodes = nodes,
        contours = listOf(PlanContour(id = "contour", edges = edges)),
    )

    private fun node(id: String, x: Double, y: Double) = PlanNode(id = id, x = x, y = y)

    private fun edge(id: String, start: String, end: String) =
        PlanEdge(id = id, startNodeId = start, endNodeId = end)

    private fun dimension(valueMm: String, updatedAt: Long) = PlanDimension(
        valueMm = valueMm,
        source = MeasurementSource.MANUAL,
        updatedAt = updatedAt,
    )

    private fun drawn(document: PlanDocument, edgeId: String): Double? =
        PlanScale.drawnMillimeters(document, edgeId)

    private fun positionOf(document: PlanDocument, nodeId: String): PlanPoint? = document.nodes
        .firstOrNull { it.id == nodeId }
        ?.let { PlanPoint(it.x, it.y) }

    private fun assertPoint(expected: PlanPoint, actual: PlanPoint?) {
        assertNotNull(actual)
        assertEquals(expected.x, actual!!.x, POINT_TOLERANCE)
        assertEquals(expected.y, actual.y, POINT_TOLERANCE)
    }

    /**
     * Every edge must keep the direction it was drawn with, whatever the fit did to its length.
     *
     * The fit solves a weighted least-squares system, so a constraint holds to about its weight
     * instead of exactly: a direction drifts by far less than a millionth of its angle, which is
     * invisible on the sheet and far below the drawing's resolution.
     */
    private fun assertDirectionsKept(before: PlanDocument, after: PlanDocument) {
        val nodesBefore = before.nodes.associateBy { it.id }
        val nodesAfter = after.nodes.associateBy { it.id }
        before.edges.forEach { edge ->
            val startBefore = nodesBefore.getValue(edge.startNodeId)
            val endBefore = nodesBefore.getValue(edge.endNodeId)
            val startAfter = nodesAfter.getValue(edge.startNodeId)
            val endAfter = nodesAfter.getValue(edge.endNodeId)
            val firstX = endBefore.x - startBefore.x
            val firstY = endBefore.y - startBefore.y
            val secondX = endAfter.x - startAfter.x
            val secondY = endAfter.y - startAfter.y
            val cross = firstX * secondY - firstY * secondX
            val scale = kotlin.math.hypot(firstX, firstY) * kotlin.math.hypot(secondX, secondY)
            assertEquals("edge ${edge.id}", 0.0, cross / scale, DIRECTION_TOLERANCE)
        }
    }

    private fun idFactory(): () -> String {
        var counter = 0
        return { "id-${counter++}" }
    }

    private companion object {
        const val PLAN_ID = "00000000-0000-0000-0000-000000000002"

        /** The fit solves numerically; a thousandth of a millimeter is far below any resolution. */
        const val MM_TOLERANCE = 1e-3
        const val POINT_TOLERANCE = 1e-5
        const val DIRECTION_TOLERANCE = 1e-6
    }
}
