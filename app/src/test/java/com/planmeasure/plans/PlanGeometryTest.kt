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
    fun clampsGridSnapToWorld() {
        assertEquals(
            PlanPoint(PlanWorld.WIDTH, PlanWorld.HEIGHT),
            PlanGeometry.snapToGrid(PlanPoint(99.0, 99.0)),
        )
        assertEquals(PlanPoint(0.0, 0.0), PlanGeometry.snapToGrid(PlanPoint(-4.0, -1.0)))
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
        assertEquals(PlanPoint(6.0, 6.0), snap?.point)
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
    fun clampsSegmentToWorldEdge() {
        val snap = PlanGeometry.snapSegment(
            nodes = emptyList(),
            startPoint = PlanPoint(18.0, 3.0),
            raw = PlanPoint(30.0, 3.0),
            magnetRadius = 0.5,
        )
        assertEquals(PlanPoint(PlanWorld.WIDTH, 3.0), snap?.point)
    }

    @Test
    fun snapsMovedNodeToGridAndWorld() {
        val node = PlanNode("n", 2.0, 2.0)

        assertEquals(
            PlanPoint(5.0, 4.0),
            PlanGeometry.snapNodeMove(listOf(node), emptyList(), node, PlanPoint(4.7, 3.6)),
        )
        assertEquals(
            PlanPoint(0.0, PlanWorld.HEIGHT),
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
