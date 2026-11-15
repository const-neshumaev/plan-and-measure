package com.planmeasure.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEditingTest {

    private val ids = idFactory()

    @Test
    fun addsFirstSegmentAsNewContour() {
        val plan = PlanEditing.addSegment(
            document = emptyPlan(),
            from = PlanPoint(2.0, 2.0),
            to = PlanPoint(8.0, 2.0),
            magnetRadius = 0.5,
            newId = ids,
        )

        assertEquals(1, plan.contours.size)
        assertEquals(1, plan.edgeCount)
        assertEquals(2, plan.nodes.size)
        assertFalse(plan.contours.single().isClosed)
    }

    @Test
    fun continuesOpenContour() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)

        assertEquals(1, plan.contours.size)
        assertEquals(2, plan.edgeCount)
        assertEquals(3, plan.nodes.size)
    }

    @Test
    fun closesContourAtFirstNode() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 8.0)
        plan = segment(plan, 8.0, 8.0, 2.0, 8.0)
        assertFalse(plan.contours.single().isClosed)

        plan = segment(plan, 2.0, 8.0, 2.0, 2.0)

        assertEquals(1, plan.contours.size)
        assertEquals(4, plan.edgeCount)
        assertTrue(plan.contours.single().isClosed)
    }

    @Test
    fun startsSecondContourAfterClosure() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 8.0)
        plan = segment(plan, 8.0, 8.0, 2.0, 8.0)
        plan = segment(plan, 2.0, 8.0, 2.0, 2.0)
        plan = segment(plan, 12.0, 12.0, 16.0, 12.0)

        assertEquals(2, plan.contours.size)
        assertEquals(5, plan.edgeCount)
    }

    @Test
    fun reusesNodeWhenNewContourStartsOnIt() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)
        // The contour now ends at (8,9), so a gesture from the middle node (8,2) becomes a
        // separate contour that shares the node instead of duplicating it.
        plan = segment(plan, 8.0, 2.0, 10.0, 2.0)

        assertEquals(2, plan.contours.size)
        assertEquals(4, plan.nodes.size)
    }

    @Test
    fun rejectsGestureThatEndsAtItsStart() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val same = PlanEditing.addSegment(
            document = plan,
            from = PlanPoint(8.0, 2.0),
            to = PlanPoint(8.0, 2.0),
            magnetRadius = 0.5,
            newId = ids,
        )
        assertSame(plan, same)
    }

    @Test
    fun movesNodeToGridCrossing() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)
        val node = plan.nodes.first { it.x == 8.0 && it.y == 2.0 }

        val moved = PlanEditing.moveNode(plan, node.id, PlanPoint(10.3, 3.6))

        assertEquals(PlanPoint(10.0, 4.0), positionOf(moved, node.id))
        assertEquals(1, moved.contours.size)
        assertEquals(3, moved.nodes.size)
    }

    @Test
    fun moveNodeKeepsEdgesAttached() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)
        val node = plan.nodes.first { it.x == 8.0 && it.y == 2.0 }

        val moved = PlanEditing.moveNode(plan, node.id, PlanPoint(11.0, 6.0))

        val edges = moved.contours.single().edges
        assertEquals(2, edges.size)
        assertTrue(edges.any { it.startNodeId == node.id || it.endNodeId == node.id })
    }

    @Test
    fun moveNodeClampsToWorld() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val node = plan.nodes.first { it.x == 8.0 }

        val moved = PlanEditing.moveNode(plan, node.id, PlanPoint(99.0, 99.0))

        assertEquals(PlanPoint(PlanWorld.WIDTH, PlanWorld.HEIGHT), positionOf(moved, node.id))
    }

    @Test
    fun moveNodeRefusesToCollapseEdge() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val node = plan.nodes.first { it.x == 2.0 }

        assertSame(plan, PlanEditing.moveNode(plan, node.id, PlanPoint(8.2, 2.3)))
        assertSame(plan, PlanEditing.moveNode(plan, node.id, PlanPoint(2.2, 2.2)))
    }

    @Test
    fun moveNodeIgnoresUnknownNode() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)

        assertSame(plan, PlanEditing.moveNode(plan, "missing", PlanPoint(4.0, 4.0)))
    }

    @Test
    fun removeEdgeSplitsContourIntoChains() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)
        plan = segment(plan, 8.0, 9.0, 2.0, 9.0)
        val middle = plan.contours.single().edges[1]

        val removed = PlanEditing.removeEdge(plan, middle.id, ids)

        assertEquals(2, removed.contours.size)
        assertEquals(2, removed.edgeCount)
        assertEquals(4, removed.nodes.size)
        assertTrue(removed.contours.none { it.isClosed })
    }

    @Test
    fun removeEdgeOpensClosedContour() {
        val plan = square()
        assertTrue(plan.contours.single().isClosed)

        val removed = PlanEditing.removeEdge(plan, plan.contours.single().edges.first().id, ids)

        assertEquals(1, removed.contours.size)
        assertEquals(3, removed.edgeCount)
        assertEquals(4, removed.nodes.size)
        assertFalse(removed.contours.single().isClosed)
    }

    @Test
    fun removeEdgeDropsEmptyContourAndUnusedNodes() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)

        val removed = PlanEditing.removeEdge(plan, plan.edges.single().id, ids)

        assertTrue(removed.contours.isEmpty())
        assertTrue(removed.nodes.isEmpty())
    }

    @Test
    fun removeEdgeIgnoresUnknownEdge() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)

        assertSame(plan, PlanEditing.removeEdge(plan, "missing", ids))
    }

    @Test
    fun clearRemovesDrawing() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val cleared = PlanEditing.clearDrawing(plan)

        assertTrue(cleared.nodes.isEmpty())
        assertTrue(cleared.contours.isEmpty())
    }

    private fun segment(
        plan: PlanDocument,
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
    ) = PlanEditing.addSegment(
        document = plan,
        from = PlanPoint(fromX, fromY),
        to = PlanPoint(toX, toY),
        magnetRadius = 0.5,
        newId = ids,
    )

    private fun square(): PlanDocument {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 8.0)
        plan = segment(plan, 8.0, 8.0, 2.0, 8.0)
        return segment(plan, 2.0, 8.0, 2.0, 2.0)
    }

    private fun positionOf(plan: PlanDocument, nodeId: String): PlanPoint? = plan.nodes
        .firstOrNull { it.id == nodeId }
        ?.let { PlanPoint(it.x, it.y) }

    private fun emptyPlan() = PlanDocument(
        id = PLAN_ID,
        name = "Room",
        createdAt = 1_000L,
    )

    private fun idFactory(): () -> String {
        var counter = 0
        return { "id-${counter++}" }
    }

    private companion object {
        const val PLAN_ID = "00000000-0000-0000-0000-000000000001"
    }
}
