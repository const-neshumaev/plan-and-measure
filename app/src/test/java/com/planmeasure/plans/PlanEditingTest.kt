package com.planmeasure.plans

import com.planmeasure.measurements.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun moveNodeGrowsSheetAroundIt() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val node = plan.nodes.first { it.x == 8.0 }

        val moved = PlanEditing.moveNode(plan, node.id, PlanPoint(221.0, 300.0))

        assertEquals(PlanPoint(221.0, 300.0), positionOf(moved, node.id))
        assertEquals(PlanSize(width = 240.0, height = 300.0), moved.world)
    }

    @Test
    fun moveNodeLeftOfOriginShiftsWholeDrawing() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val left = plan.nodes.first { it.x == 2.0 }
        val right = plan.nodes.first { it.x == 8.0 }

        val moved = PlanEditing.moveNode(plan, left.id, PlanPoint(-1.0, 2.0))

        assertEquals(PlanPoint(19.0, 2.0), positionOf(moved, left.id))
        assertEquals(PlanPoint(28.0, 2.0), positionOf(moved, right.id))
        assertEquals(PlanSize(), moved.world)
    }

    @Test
    fun addsSegmentPastBorderGrowsSheet() {
        val plan = segment(emptyPlan(), 198.0, 2.0, 225.0, 2.0)

        assertEquals(PlanSize(width = 240.0, height = 280.0), plan.world)
        assertEquals(2, plan.nodes.size)
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
    fun weldsNodeDroppedOnAnotherVertex() {
        val dimension = PlanDimension(
            valueMm = "3200",
            source = MeasurementSource.MANUAL,
            updatedAt = 2_000L,
        )
        val plan = PlanDocument(
            id = PLAN_ID,
            name = "Room",
            createdAt = 1_000L,
            nodes = listOf(
                PlanNode("a", 2.0, 2.0),
                PlanNode("b", 8.0, 2.0),
                PlanNode("c", 8.0, 6.0),
                PlanNode("d", 12.0, 6.0),
            ),
            contours = listOf(
                PlanContour("first", listOf(PlanEdge("e1", "a", "b"))),
                PlanContour("second", listOf(PlanEdge("e2", "c", "d", dimension))),
            ),
        )

        val welded = PlanEditing.moveNode(plan, "c", PlanPoint(8.2, 2.1), magnetRadius = 0.5)

        assertEquals(listOf("a", "b", "d"), welded.nodes.map { it.id })
        assertEquals(2, welded.edgeCount)
        assertEquals("b", welded.edges.first { it.id == "e2" }.startNodeId)
        assertEquals(dimension, welded.edges.first { it.id == "e2" }.dimension)

        // The welded vertex is shared now: moving it drags both walls.
        val dragged = PlanEditing.moveNode(welded, "b", PlanPoint(9.0, 3.0))
        assertEquals(PlanPoint(9.0, 3.0), positionOf(dragged, "b"))
        assertEquals(2, dragged.edges.count { it.startNodeId == "b" || it.endNodeId == "b" })
    }

    @Test
    fun weldsNodesWhenGridSnapLandsOnExistingVertex() {
        val plan = PlanDocument(
            id = PLAN_ID,
            name = "Room",
            createdAt = 1_000L,
            nodes = listOf(
                PlanNode("a", 2.0, 2.0),
                PlanNode("b", 8.0, 2.0),
                PlanNode("c", 8.0, 6.0),
                PlanNode("d", 12.0, 6.0),
            ),
            contours = listOf(
                PlanContour("first", listOf(PlanEdge("e1", "a", "b"))),
                PlanContour("second", listOf(PlanEdge("e2", "c", "d"))),
            ),
        )

        val welded = PlanEditing.moveNode(plan, "c", PlanPoint(7.8, 2.2), magnetRadius = 0.0)

        assertEquals(listOf("a", "b", "d"), welded.nodes.map { it.id })
        assertEquals("b", welded.edges.first { it.id == "e2" }.startNodeId)
    }

    @Test
    fun refusesWeldThatWouldDuplicateAWall() {
        val plan = PlanDocument(
            id = PLAN_ID,
            name = "Room",
            createdAt = 1_000L,
            nodes = listOf(
                PlanNode("a", 2.0, 2.0),
                PlanNode("b", 5.0, 5.0),
                PlanNode("x", 2.0, 6.0),
            ),
            contours = listOf(
                PlanContour("first", listOf(PlanEdge("e1", "a", "x"))),
                PlanContour("second", listOf(PlanEdge("e2", "b", "x"))),
            ),
        )

        assertSame(plan, PlanEditing.moveNode(plan, "a", PlanPoint(5.2, 4.8), magnetRadius = 0.5))
    }

    @Test
    fun movesEdgeKeepingDirectionAndLength() {
        val plan = square()
        val bottom = plan.contours.single().edges.first()

        val moved = PlanEditing.moveEdge(plan, bottom.id, PlanPoint(5.0, 2.0), PlanPoint(5.4, 4.2))

        assertEquals(PlanPoint(2.0, 4.0), positionOf(moved, bottom.startNodeId))
        assertEquals(PlanPoint(8.0, 4.0), positionOf(moved, bottom.endNodeId))
        assertEquals(1, moved.contours.size)
        assertEquals(4, moved.nodes.size)
    }

    @Test
    fun moveEdgeLeavesOtherNodesInPlace() {
        val plan = square()
        val bottom = plan.contours.single().edges.first()
        val moved = PlanEditing.moveEdge(plan, bottom.id, PlanPoint(5.0, 2.0), PlanPoint(5.0, 3.0))

        val shifted = setOf(bottom.startNodeId, bottom.endNodeId)
        plan.nodes.filterNot { it.id in shifted }.forEach { node ->
            assertEquals(node, moved.nodes.first { it.id == node.id })
        }
    }

    @Test
    fun moveEdgeSnapsOffsetToWholeUnits() {
        val plan = square()
        val bottom = plan.contours.single().edges.first()

        // A small diagonal offset rounds to one cell to the right, and the neighbours become
        // slanted because only one of their two nodes travelled with the edge.
        val moved = PlanEditing.moveEdge(plan, bottom.id, PlanPoint(5.0, 2.0), PlanPoint(5.6, 2.4))

        assertEquals(PlanPoint(3.0, 2.0), positionOf(moved, bottom.startNodeId))
        assertEquals(PlanPoint(9.0, 2.0), positionOf(moved, bottom.endNodeId))
    }

    @Test
    fun moveEdgeClampsToSheet() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()

        val moved = PlanEditing.moveEdge(plan, edge.id, PlanPoint(5.0, 2.0), PlanPoint(500.0, 2.0))

        // The edge stops at the sheet border without changing its length.
        assertEquals(PlanPoint(PlanWorld.DEFAULT_WIDTH - 6.0, 2.0), positionOf(moved, edge.startNodeId))
        assertEquals(PlanPoint(PlanWorld.DEFAULT_WIDTH, 2.0), positionOf(moved, edge.endNodeId))
        assertEquals(PlanSize(), moved.world)
    }

    @Test
    fun moveEdgeRefusesToCollapseNeighbour() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 6.0)
        val first = plan.contours.single().edges.first()

        // Up by four cells puts the shared node on top of the far end of the second edge.
        assertSame(plan, PlanEditing.moveEdge(plan, first.id, PlanPoint(5.0, 2.0), PlanPoint(5.0, 6.0)))
    }

    @Test
    fun moveEdgeIgnoresZeroOffset() {
        val plan = square()
        val bottom = plan.contours.single().edges.first()

        assertSame(plan, PlanEditing.moveEdge(plan, bottom.id, PlanPoint(5.0, 2.0), PlanPoint(5.2, 2.1)))
    }

    @Test
    fun moveEdgeIgnoresUnknownEdge() {
        val plan = square()

        assertSame(plan, PlanEditing.moveEdge(plan, "missing", PlanPoint(5.0, 2.0), PlanPoint(5.0, 4.0)))
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
    fun fitsSheetToDrawingWrittenOutsideIt() {
        val plan = PlanDocument(
            id = PLAN_ID,
            name = "Corridor",
            createdAt = 1_000L,
            world = PlanSize(width = 20.0, height = 28.0),
            nodes = listOf(PlanNode("a", 25.0, 300.0)),
        )

        val fitted = PlanEditing.fitWorldToDrawing(plan)

        assertEquals(PlanSize(width = 40.0, height = 300.0), fitted.world)
        assertEquals(PlanPoint(25.0, 300.0), positionOf(fitted, "a"))
        assertSame(fitted, PlanEditing.fitWorldToDrawing(fitted))
    }

    @Test
    fun trimsSheetOfScaledPlanWhenOpened() {
        val plan = PlanDocument(
            id = PLAN_ID,
            name = "Room",
            createdAt = 1_000L,
            world = PlanSize(width = 400.0, height = 300.0),
            mmPerUnit = 41.0,
            nodes = listOf(PlanNode("a", 41.0, 52.0), PlanNode("b", 83.0, 52.0)),
        )

        val fitted = PlanEditing.fitWorldToDrawing(plan)

        // 42 x 0 units rounded up to the 20-step sheet, the drawing centred on it.
        assertEquals(PlanSize(width = 60.0, height = 20.0), fitted.world)
        assertEquals(PlanPoint(9.0, 10.0), positionOf(fitted, "a"))
        assertEquals(PlanPoint(51.0, 10.0), positionOf(fitted, "b"))
        assertSame(fitted, PlanEditing.fitWorldToDrawing(fitted))
    }

    @Test
    fun clearRemovesDrawing() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val cleared = PlanEditing.clearDrawing(plan)

        assertTrue(cleared.nodes.isEmpty())
        assertTrue(cleared.contours.isEmpty())
    }

    @Test
    fun setDimensionAttachesToThePickedEdgeOnly() {
        val plan = square()
        val bottom = plan.contours.single().edges.first()
        val dimension = PlanDimension(
            valueMm = "3200",
            source = MeasurementSource.MANUAL,
            updatedAt = 2_000L,
        )

        val updated = PlanEditing.setDimension(plan, bottom.id, dimension)

        assertEquals(dimension, updated.edges.first { it.id == bottom.id }.dimension)
        assertTrue(updated.edges.filterNot { it.id == bottom.id }.all { it.dimension == null })
        // The sheet is trimmed to the drawing, so only the position on the sheet may change.
        assertEquals(6.0, updated.nodes.maxOf { it.x } - updated.nodes.minOf { it.x }, 1e-9)
        assertEquals(6.0, updated.nodes.maxOf { it.y } - updated.nodes.minOf { it.y }, 1e-9)
        assertEquals(plan.contours.single().edges.map { it.id }, updated.edges.map { it.id })
    }

    @Test
    fun setDimensionIgnoresUnknownEdgeAndRepeatedValue() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()
        val dimension = PlanDimension(
            valueMm = "457.2",
            source = MeasurementSource.RANGFINDER,
            updatedAt = 2_000L,
        )
        val annotated = PlanEditing.setDimension(plan, edge.id, dimension)

        assertSame(plan, PlanEditing.setDimension(plan, "missing", dimension))
        assertSame(annotated, PlanEditing.setDimension(annotated, edge.id, dimension))
    }

    @Test
    fun removeDimensionKeepsTheEdge() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()
        val annotated = PlanEditing.setDimension(
            document = plan,
            edgeId = edge.id,
            dimension = PlanDimension(
                valueMm = "1500",
                source = MeasurementSource.MANUAL,
                updatedAt = 2_000L,
            ),
        )

        val cleared = PlanEditing.removeDimension(annotated, edge.id)

        assertEquals(1, cleared.edgeCount)
        assertNull(cleared.edges.single().dimension)
        assertSame(cleared, PlanEditing.removeDimension(cleared, edge.id))
        assertSame(cleared, PlanEditing.removeDimension(cleared, "missing"))
    }

    @Test
    fun dimensionSurvivesMovingItsEdge() {
        val plan = square()
        val bottom = plan.contours.single().edges.first()
        val dimension = PlanDimension(
            valueMm = "3200",
            source = MeasurementSource.MANUAL,
            updatedAt = 2_000L,
        )
        val annotated = PlanEditing.setDimension(plan, bottom.id, dimension)

        val moved = PlanEditing.moveEdge(
            document = annotated,
            edgeId = bottom.id,
            from = PlanPoint(5.0, 2.0),
            to = PlanPoint(5.4, 4.2),
        )

        assertEquals(dimension, moved.edges.first { it.id == bottom.id }.dimension)
    }

    @Test
    fun removingAnEdgeRemovesItsDimension() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()
        val annotated = PlanEditing.setDimension(
            document = plan,
            edgeId = edge.id,
            dimension = PlanDimension(
                valueMm = "1500",
                source = MeasurementSource.MANUAL,
                updatedAt = 2_000L,
            ),
        )

        val removed = PlanEditing.removeEdge(annotated, edge.id, ids)

        assertTrue(removed.edges.none { it.id == edge.id })
    }

    @Test
    fun splitEdgeReplacesItWithTwoIndependentEdges() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()

        val split = PlanEditing.splitEdge(plan, edge.id, PlanPoint(5.4, 2.3), ids)

        val edges = split.contours.single().edges
        assertEquals(2, edges.size)
        assertEquals(3, split.nodes.size)
        assertEquals(edge.id, edges[0].id)
        assertEquals(edge.startNodeId, edges[0].startNodeId)
        assertEquals(edge.endNodeId, edges[1].endNodeId)
        assertEquals(edges[0].endNodeId, edges[1].startNodeId)
        assertTrue(edges[0].id != edges[1].id)
        // The point stays where the wall was touched instead of jumping to a grid crossing.
        assertEquals(5.4, positionOf(split, edges[0].endNodeId)?.x ?: 0.0, 1e-9)
        assertEquals(2.0, positionOf(split, edges[0].endNodeId)?.y ?: 0.0, 1e-9)
    }

    @Test
    fun splitEdgeKeepsContourOrderAndClosure() {
        val plan = square()
        val first = plan.contours.single().edges.first()

        val split = PlanEditing.splitEdge(plan, first.id, PlanPoint(5.0, 2.0), ids)

        assertEquals(1, split.contours.size)
        assertEquals(5, split.edgeCount)
        assertTrue(split.contours.single().isClosed)
        assertEquals(first.id, split.contours.single().edges.first().id)
    }

    @Test
    fun splitEdgeWorksOnMiddleEdgeOfAnOpenContour() {
        var plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        plan = segment(plan, 8.0, 2.0, 8.0, 9.0)
        plan = segment(plan, 8.0, 9.0, 2.0, 9.0)
        val before = plan.contours.single().edges
        val middle = before[1]

        val split = PlanEditing.splitEdge(plan, middle.id, PlanPoint(8.2, 5.6), ids)

        val edges = split.contours.single().edges
        assertEquals(4, edges.size)
        assertEquals(before[0], edges[0])
        assertEquals(before[2], edges[3])
        assertEquals(middle.id, edges[1].id)
        assertEquals(8.0, positionOf(split, edges[1].endNodeId)?.x ?: 0.0, 1e-9)
        assertEquals(5.6, positionOf(split, edges[1].endNodeId)?.y ?: 0.0, 1e-9)
        assertEquals(edges[1].endNodeId, edges[2].startNodeId)
    }

    @Test
    fun splitEdgeRemovesDimensionFromBothHalves() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()
        val annotated = PlanEditing.setDimension(
            document = plan,
            edgeId = edge.id,
            dimension = PlanDimension(
                valueMm = "3200",
                source = MeasurementSource.MANUAL,
                updatedAt = 2_000L,
            ),
        )

        val start = requireNotNull(positionOf(annotated, edge.startNodeId))
        val end = requireNotNull(positionOf(annotated, edge.endNodeId))
        val middle = PlanPoint((start.x + end.x) / 2, (start.y + end.y) / 2)

        val split = PlanEditing.splitEdge(annotated, edge.id, middle, ids)

        assertEquals(2, split.edgeCount)
        assertTrue(split.edges.all { it.dimension == null })
    }

    @Test
    fun splitEdgeKeepsSlantedWallStraight() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 6.0)
        val edge = plan.edges.single()

        val split = PlanEditing.splitEdge(plan, edge.id, PlanPoint(4.0, 2.5), ids)

        val middle = split.nodes.first { it.id != edge.startNodeId && it.id != edge.endNodeId }
        // The inserted node keeps the exact projection: rounding it to the grid would bend the
        // wall, which a window mark must not do.
        assertEquals(2.0 + (middle.x - 2.0) * 2.0 / 3.0, middle.y, 1e-9)
        assertTrue(middle.x % 1.0 != 0.0)
        assertEquals(PlanPoint(2.0, 2.0), positionOf(split, edge.startNodeId))
        assertEquals(PlanPoint(8.0, 6.0), positionOf(split, edge.endNodeId))
    }

    @Test
    fun splitEdgeIgnoresPointTooCloseToAnEnd() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()

        assertSame(plan, PlanEditing.splitEdge(plan, edge.id, PlanPoint(2.4, 2.0), ids))
        assertSame(plan, PlanEditing.splitEdge(plan, edge.id, PlanPoint(7.8, 2.0), ids))
    }

    @Test
    fun canSplitEdgeMatchesWhatSplitAccepts() {
        val plan = segment(emptyPlan(), 2.0, 2.0, 8.0, 2.0)
        val edge = plan.edges.single()

        assertTrue(PlanEditing.canSplitEdge(plan, edge.id, PlanPoint(5.0, 2.0)))
        assertFalse(PlanEditing.canSplitEdge(plan, edge.id, PlanPoint(2.4, 2.0)))
        assertFalse(PlanEditing.canSplitEdge(plan, "missing", PlanPoint(5.0, 2.0)))
    }

    @Test
    fun splitEdgeIgnoresUnknownEdge() {
        val plan = square()

        assertSame(plan, PlanEditing.splitEdge(plan, "missing", PlanPoint(5.0, 2.0), ids))
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
