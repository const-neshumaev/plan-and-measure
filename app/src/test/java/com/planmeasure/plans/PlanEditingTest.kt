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
    fun undoRemovesLastEdgeAndUnusedNode() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)

        val undone = PlanEditing.undoLastSegment(plan)

        assertEquals(1, undone.edgeCount)
        assertEquals(2, undone.nodes.size)
    }

    @Test
    fun undoReopensClosedContour() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 8.0)
        plan = segment(plan, 8.0, 8.0, 2.0, 8.0)
        plan = segment(plan, 2.0, 8.0, 2.0, 2.0)

        val undone = PlanEditing.undoLastSegment(plan)

        assertEquals(3, undone.edgeCount)
        assertFalse(undone.contours.single().isClosed)
    }

    @Test
    fun undoOnEmptyPlanChangesNothing() {
        val plan = emptyPlan()
        assertSame(plan, PlanEditing.undoLastSegment(plan))
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
