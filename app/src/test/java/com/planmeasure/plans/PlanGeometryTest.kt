package com.planmeasure.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanGeometryTest {

    @Test
    fun snapsToNearestGridCrossing() {
        assertEquals(PlanPoint(3.0, 5.0), PlanGeometry.snapToGrid(PlanPoint(3.2, 4.6)))
    }

    @Test
    fun clampsGridSnapToCoordinateLimit() {
        assertEquals(
            PlanPoint(PlanWorld.MAX_COORDINATE, PlanWorld.MAX_COORDINATE),
            PlanGeometry.snapToGrid(PlanPoint(99_999.0, 99_999.0)),
        )
        assertEquals(
            PlanPoint(-PlanWorld.MAX_COORDINATE, -PlanWorld.MAX_COORDINATE),
            PlanGeometry.snapToGrid(PlanPoint(-99_999.0, -99_999.0)),
        )
    }

    @Test
    fun roundsSegmentToAxis() {
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(5.0, 5.0),
            raw = PlanPoint(9.2, 5.4),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(9.0, 5.0), snap?.point)
    }

    @Test
    fun roundsSegmentToDiagonal() {
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(2.0, 2.0),
            raw = PlanPoint(4.2, 4.1),
            magnetRadius = 0.5,
        )
        // A 45° step is √2 long, so the end must stay under the finger instead of doubling it.
        assertEquals(PlanPoint(4.0, 4.0), snap?.point)
    }

    @Test
    fun extendsDiagonalSegmentPastSheet() {
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(18.0, 2.0),
            raw = PlanPoint(30.0, 14.0),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(30.0, 14.0), snap?.point)
    }

    @Test
    fun magnetAlignsSegmentAimedNearAxis() {
        // The finger is about 10° off, so the magnet straightens the edge instead of sloping it.
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(5.0, 5.0),
            raw = PlanPoint(11.4, 6.1),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(11.0, 5.0), snap?.point)
    }

    @Test
    fun keepsAngleDrawnFarFromMagnet() {
        // About 29° from the axis is a deliberate slant, so only the end is snapped to the grid.
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(2.0, 2.0),
            raw = PlanPoint(8.2, 5.4),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(8.0, 5.0), snap?.point)
    }

    @Test
    fun rejectsShortDrag() {
        assertNull(
            PlanGeometry.snapSegment(emptyList(), PlanPoint(5.0, 5.0), PlanPoint(5.3, 5.2), 0.5),
        )
        assertNull(
            PlanGeometry.snapSegment(emptyList(), PlanPoint(5.0, 5.0), PlanPoint(5.6, 5.6), 0.5),
        )
    }

    @Test
    fun snapsToExistingNodeInsteadOfDirection() {
        val node = PlanNode("n", 7.0, 4.0)
        val snap = PlanGeometry.snapSegment(
            nodes = listOf(node),
            startPoint = PlanPoint(5.0, 5.0),
            raw = PlanPoint(7.2, 4.1),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(7.0, 4.0), snap?.point)
        assertEquals(node, snap?.node)
    }

    @Test
    fun refusesToSnapBackToStartNode() {
        val node = PlanNode("n", 5.0, 5.0)
        assertNull(
            PlanGeometry.snapSegment(listOf(node), PlanPoint(5.0, 5.0), PlanPoint(5.2, 5.1), 0.5),
        )
    }

    @Test
    fun extendsSegmentPastSheet() {
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(18.0, 3.0),
            raw = PlanPoint(30.0, 3.0),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(30.0, 3.0), snap?.point)
    }

    @Test
    fun snapsMovedNodeToGridWithoutSheetClamp() {
        val node = PlanNode("n", 2.0, 2.0)

        assertEquals(
            PlanPoint(5.0, 4.0),
            PlanGeometry.snapNodeMove(listOf(node), emptyList(), node, PlanPoint(4.7, 3.6)),
        )
        // Pulling the node past the border grows the sheet instead of refusing the move.
        assertEquals(
            PlanPoint(-3.0, 99.0),
            PlanGeometry.snapNodeMove(listOf(node), emptyList(), node, PlanPoint(-3.0, 99.0)),
        )
    }

    @Test
    fun refusesNodeMoveThatCollapsesEdge() {
        val first = PlanNode("a", 2.0, 2.0)
        val second = PlanNode("b", 3.0, 2.0)
        val edge = PlanEdge("e", "a", "b")

        assertNull(
            PlanGeometry.snapNodeMove(listOf(first, second), listOf(edge), first, PlanPoint(2.6, 2.0)),
        )
        assertEquals(
            PlanPoint(2.0, 4.0),
            PlanGeometry.snapNodeMove(listOf(first, second), listOf(edge), first, PlanPoint(2.2, 3.8)),
        )
    }

    @Test
    fun magnetSnapsMovedNodeToExistingVertex() {
        val moved = PlanNode("a", 2.0, 2.0)
        val target = PlanNode("b", 5.0, 5.0)
        val neighbour = PlanNode("c", 2.0, 6.0)
        val edge = PlanEdge("e", "a", "c")

        assertEquals(
            PlanPoint(5.0, 5.0),
            PlanGeometry.snapNodeMove(
                nodes = listOf(moved, target, neighbour),
                edges = listOf(edge),
                node = moved,
                raw = PlanPoint(5.4, 4.7),
                magnetRadius = 0.5,
            ),
        )
    }

    @Test
    fun findsNodeStandingOnPoint() {
        val node = PlanNode("n", 4.0, 7.0)

        assertEquals(node, PlanGeometry.nodeAt(listOf(node), PlanPoint(4.0, 7.0)))
        assertNull(PlanGeometry.nodeAt(listOf(node), PlanPoint(4.0, 7.0), excludeId = "n"))
        assertNull(PlanGeometry.nodeAt(listOf(node), PlanPoint(5.0, 7.0)))
    }

    @Test
    fun refusesMagnetThatWouldDuplicateAWall() {
        val moved = PlanNode("a", 2.0, 2.0)
        val target = PlanNode("b", 5.0, 5.0)
        val shared = PlanNode("x", 2.0, 6.0)
        val edges = listOf(PlanEdge("e1", "a", "x"), PlanEdge("e2", "b", "x"))

        assertNull(
            PlanGeometry.snapNodeMove(
                nodes = listOf(moved, target, shared),
                edges = edges,
                node = moved,
                raw = PlanPoint(5.2, 4.8),
                magnetRadius = 0.5,
            ),
        )
    }

    @Test
    fun offsetsEdgeAsAWhole() {
        val nodes = listOf(PlanNode("a", 2.0, 2.0), PlanNode("b", 8.0, 2.0))
        val edge = PlanEdge("e", "a", "b")

        assertEquals(
            PlanPoint(0.0, 3.0),
            PlanGeometry.snapEdgeMove(
                nodes = nodes,
                edges = listOf(edge),
                world = PlanSize(),
                edge = edge,
                from = PlanPoint(5.0, 2.0),
                to = PlanPoint(5.4, 5.2),
            ),
        )
    }

    @Test
    fun clampsEdgeOffsetToWorld() {
        val nodes = listOf(PlanNode("a", 2.0, 2.0), PlanNode("b", 8.0, 2.0))
        val edge = PlanEdge("e", "a", "b")

        assertEquals(
            PlanPoint(-2.0, 0.0),
            PlanGeometry.snapEdgeMove(
                nodes = nodes,
                edges = listOf(edge),
                world = PlanSize(),
                edge = edge,
                from = PlanPoint(5.0, 2.0),
                to = PlanPoint(-5.0, 2.0),
            ),
        )
    }

    @Test
    fun refusesEdgeOffsetThatCollapsesNeighbour() {
        val nodes = listOf(
            PlanNode("a", 2.0, 2.0),
            PlanNode("b", 8.0, 2.0),
            PlanNode("c", 8.0, 6.0),
        )
        val moved = PlanEdge("first", "a", "b")
        val neighbour = PlanEdge("second", "b", "c")

        assertNull(
            PlanGeometry.snapEdgeMove(
                nodes = nodes,
                edges = listOf(moved, neighbour),
                world = PlanSize(),
                edge = moved,
                from = PlanPoint(5.0, 2.0),
                to = PlanPoint(5.0, 6.0),
            ),
        )
    }

    @Test
    fun projectsFingerOntoEdge() {
        val nodes = listOf(PlanNode("a", 2.0, 2.0), PlanNode("b", 8.0, 2.0))
        val edge = PlanEdge("e", "a", "b")

        assertEquals(PlanPoint(5.0, 2.0), PlanGeometry.projectOnEdge(nodes, edge, PlanPoint(5.0, 3.0)))
        assertEquals(PlanPoint(2.0, 2.0), PlanGeometry.projectOnEdge(nodes, edge, PlanPoint(0.0, 2.0)))
        assertNull(PlanGeometry.projectOnEdge(emptyList(), edge, PlanPoint(5.0, 3.0)))
    }

    @Test
    fun findsEdgeUnderFinger() {
        val nodes = listOf(PlanNode("a", 2.0, 2.0), PlanNode("b", 8.0, 2.0))
        val contours = listOf(PlanContour("c", listOf(PlanEdge("e", "a", "b"))))

        assertEquals("e", PlanGeometry.nearestEdge(nodes, contours, PlanPoint(5.0, 2.3), 0.5)?.id)
        assertNull(PlanGeometry.nearestEdge(nodes, contours, PlanPoint(5.0, 3.0), 0.5))
    }

    @Test
    fun measuresTapBeyondEdgeEndFromItsNode() {
        val nodes = listOf(PlanNode("a", 2.0, 2.0), PlanNode("b", 8.0, 2.0))
        val contours = listOf(PlanContour("c", listOf(PlanEdge("e", "a", "b"))))

        assertEquals("e", PlanGeometry.nearestEdge(nodes, contours, PlanPoint(9.0, 2.0), 1.5)?.id)
        assertNull(PlanGeometry.nearestEdge(nodes, contours, PlanPoint(10.0, 2.0), 1.5))
    }

    @Test
    fun picksNearestOfTwoEdges() {
        val nodes = listOf(
            PlanNode("a", 2.0, 2.0),
            PlanNode("b", 8.0, 2.0),
            PlanNode("c", 8.0, 8.0),
        )
        val contour = PlanContour(
            "c",
            listOf(PlanEdge("first", "a", "b"), PlanEdge("second", "b", "c")),
        )

        assertEquals(
            "second",
            PlanGeometry.nearestEdge(nodes, listOf(contour), PlanPoint(7.7, 5.0), 1.0)?.id,
        )
    }

    @Test
    fun picksNearestNodeWithinRadius() {
        val nodes = listOf(PlanNode("a", 1.0, 1.0), PlanNode("b", 2.0, 1.0))
        assertEquals("b", PlanGeometry.nearestNode(nodes, PlanPoint(1.8, 1.0), 1.0)?.id)
        assertNull(PlanGeometry.nearestNode(nodes, PlanPoint(5.0, 5.0), 1.0))
    }

    @Test
    fun growsSheetToFitNodePastBorder() {
        val growth = PlanGeometry.growWorldToFit(PlanSize(), listOf(PlanPoint(221.0, 4.0)))

        assertEquals(PlanSize(width = 240.0, height = 280.0), growth?.size)
        assertEquals(PlanPoint(0.0, 0.0), growth?.shift)
    }

    @Test
    fun doesNotGrowSheetWhenDrawingFits() {
        assertNull(PlanGeometry.growWorldToFit(PlanSize(), listOf(PlanPoint(20.0, 28.0))))
        assertNull(
            PlanGeometry.growWorldToFit(
                PlanSize(),
                listOf(PlanPoint(PlanWorld.DEFAULT_WIDTH, PlanWorld.DEFAULT_HEIGHT)),
            ),
        )
        assertNull(PlanGeometry.growWorldToFit(PlanSize(), emptyList()))
    }

    @Test
    fun shiftsDrawingBackWhenNodeGoesLeftOfOrigin() {
        val growth = PlanGeometry.growWorldToFit(
            world = PlanSize(),
            points = listOf(PlanPoint(-1.0, 2.0), PlanPoint(19.0, 2.0)),
        )

        // The drawing moves right by a whole step; the default sheet already has room for it.
        assertEquals(PlanPoint(20.0, 0.0), growth?.shift)
        assertEquals(PlanSize(), growth?.size)
    }

    @Test
    fun growsSheetWithoutAnUpperBound() {
        val growth = PlanGeometry.growWorldToFit(
            world = PlanSize(),
            points = listOf(PlanPoint(88.0, 300.0)),
        )

        // The sheet has to contain the node that caused the growth; a cap here left it outside.
        assertEquals(PlanSize(width = 200.0, height = 300.0), growth?.size)
    }

    @Test
    fun trimsSheetToTheDrawingAndCentresIt() {
        val trim = PlanGeometry.trimWorldToDrawing(
            world = PlanSize(width = 400.0, height = 300.0),
            points = listOf(PlanPoint(2.0, 2.0), PlanPoint(88.0, 123.0)),
        )

        // 86 x 121 units rounded up to the 20-step sheet, the drawing centred on it.
        assertEquals(PlanSize(width = 100.0, height = 140.0), trim?.size)
        assertEquals(PlanPoint(5.0, 7.5), trim?.shift)
    }

    @Test
    fun doesNotTrimSheetItCannotShrink() {
        // An old sheet smaller than the step cannot be cut further, and a drawing that already fills
        // the sheet does not move it either.
        assertNull(
            PlanGeometry.trimWorldToDrawing(
                world = PlanSize(width = 20.0, height = 28.0),
                points = listOf(PlanPoint(1.0, 1.0), PlanPoint(19.0, 27.0)),
            ),
        )
        assertNull(
            PlanGeometry.trimWorldToDrawing(
                world = PlanSize(),
                points = listOf(
                    PlanPoint(0.0, 0.0),
                    PlanPoint(PlanWorld.DEFAULT_WIDTH, PlanWorld.DEFAULT_HEIGHT),
                ),
            ),
        )
        assertNull(PlanGeometry.trimWorldToDrawing(PlanSize(), emptyList()))
    }

    @Test
    fun startSnapsToNodeUnderFinger() {
        val node = PlanNode("n", 3.0, 4.0)
        assertEquals(
            PlanPoint(3.0, 4.0),
            PlanGeometry.snapStart(listOf(node), PlanPoint(3.4, 4.2), 0.5),
        )
        assertEquals(
            PlanPoint(7.0, 8.0),
            PlanGeometry.snapStart(emptyList(), PlanPoint(6.7, 8.2), 0.5),
        )
        assertNotNull(PlanGeometry.snapStart(emptyList(), PlanPoint(0.0, 0.0), 0.5))
    }
}
