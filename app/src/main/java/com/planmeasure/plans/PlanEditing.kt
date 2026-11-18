package com.planmeasure.plans

import kotlin.math.hypot

/**
 * Applies drawing and editing gestures to a plan.
 *
 * One drawing gesture adds one edge. The edge continues the contour that ends at its start node,
 * otherwise it starts a new contour. Ending an edge on the first node of a contour closes it.
 * Editing moves a node, which drags every edge that touches it, slides one edge as a whole,
 * removes one edge, or inserts a node on an edge and thus splits it into two independent edges.
 * Anything that leaves the sheet grows it, see [PlanGeometry.growWorldToFit].
 *
 * Dimensions are annotations of edges: attaching or removing one never changes coordinates
 * (ADR 0007).
 */
object PlanEditing {

    /**
     * Adds a segment from [from] to [to] snapped by [PlanGeometry].
     *
     * [newId] is called for every node, edge and contour that has to be created. A segment pulled
     * past the border grows the sheet. Returns the same instance when the gesture produces no
     * usable segment.
     */
    fun addSegment(
        document: PlanDocument,
        from: PlanPoint,
        to: PlanPoint,
        magnetRadius: Double,
        newId: () -> String,
    ): PlanDocument {
        val magnetStart = PlanGeometry.nearestNode(document.nodes, from, magnetRadius)
        val startNode = magnetStart ?: PlanGeometry.snapToGrid(from).let {
            PlanNode(id = newId(), x = it.x, y = it.y)
        }
        val startPoint = PlanPoint(startNode.x, startNode.y)

        val snap = PlanGeometry.snapSegment(document.nodes, startPoint, to, magnetRadius)
            ?: return document
        val endNode = snap.node ?: PlanNode(id = newId(), x = snap.point.x, y = snap.point.y)
        if (endNode.id == startNode.id) return document

        val nodes = document.nodes
            .plusIfAbsent(startNode)
            .plusIfAbsent(endNode)
        val edge = PlanEdge(id = newId(), startNodeId = startNode.id, endNodeId = endNode.id)

        val contourIndex = document.contours.indexOfLast { contour ->
            !contour.isClosed && contour.edges.lastOrNull()?.endNodeId == startNode.id
        }
        val contours = if (contourIndex >= 0) {
            document.contours.mapIndexed { index, contour ->
                if (index == contourIndex) contour.copy(edges = contour.edges + edge) else contour
            }
        } else {
            document.contours + PlanContour(id = newId(), edges = listOf(edge))
        }

        return document.copy(nodes = nodes, contours = contours).withWorldFittingDrawing()
    }

    /**
     * Moves [nodeId] to [to], snapped to the grid by [PlanGeometry.snapNodeMove].
     *
     * A node is shared by every edge that touches it, so the whole shape follows it. Dropping the
     * node on another vertex welds the two together: the edges of the moved node are reattached to
     * the target, and the empty node disappears. A node pulled past the border grows the sheet.
     * Returns the same instance when the node is unknown, the target would collapse an incident
     * edge or welding would create two walls between the same pair of nodes.
     *
     * [magnetRadius] is in world units and comes from the same screen distance as the magnet of a
     * drawn segment, so both tools feel alike.
     */
    fun moveNode(
        document: PlanDocument,
        nodeId: String,
        to: PlanPoint,
        magnetRadius: Double = 0.0,
    ): PlanDocument {
        val node = document.nodes.firstOrNull { it.id == nodeId } ?: return document
        val target = PlanGeometry.snapNodeMove(
            nodes = document.nodes,
            edges = document.edges,
            node = node,
            raw = to,
            magnetRadius = magnetRadius,
        ) ?: return document

        val targetNode = PlanGeometry.nodeAt(document.nodes, target, excludeId = node.id)
        if (targetNode != null) {
            return document.mergeNodeInto(movedId = node.id, targetId = targetNode.id) ?: document
        }

        if (target.x == node.x && target.y == node.y) return document
        return document.copy(
            nodes = document.nodes.map { current ->
                if (current.id == nodeId) current.copy(x = target.x, y = target.y) else current
            },
        ).withWorldFittingDrawing()
    }

    /**
     * Slides [edgeId] to the point the finger reached; see [PlanGeometry.snapEdgeMove].
     *
     * Both nodes of the edge are translated by one offset, so the edge keeps its direction and
     * length, and the edges that share those nodes follow. A plan that slides a diagonal wall along
     * itself therefore gets slanted neighbours; that is visible during the drag and undoable.
     *
     * [from] is the grabbed point of the edge and [to] the current finger position, both in world
     * units. Returns the same instance when the edge is unknown or the offset is not allowed.
     */
    fun moveEdge(
        document: PlanDocument,
        edgeId: String,
        from: PlanPoint,
        to: PlanPoint,
    ): PlanDocument {
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return document
        val offset = PlanGeometry.snapEdgeMove(
            nodes = document.nodes,
            edges = document.edges,
            world = document.world,
            edge = edge,
            from = from,
            to = to,
        ) ?: return document
        if (offset.x == 0.0 && offset.y == 0.0) return document
        val moved = setOf(edge.startNodeId, edge.endNodeId)
        return document.copy(
            nodes = document.nodes.map { node ->
                if (node.id in moved) {
                    node.copy(x = node.x + offset.x, y = node.y + offset.y)
                } else {
                    node
                }
            },
        )
    }

    /**
     * Removes the edge [edgeId] and drops nodes that no longer belong to any edge.
     *
     * A contour is a chain of connected edges, so removing a middle edge splits it: the leftovers
     * become separate contours instead of one contour whose edges are no longer connected.
     * [newId] is called for the contours created by a split.
     */
    fun removeEdge(
        document: PlanDocument,
        edgeId: String,
        newId: () -> String,
    ): PlanDocument {
        if (document.edges.none { it.id == edgeId }) return document

        val contours = mutableListOf<PlanContour>()
        document.contours.forEach { contour ->
            val remaining = contour.edges.filterNot { it.id == edgeId }
            if (remaining.isEmpty()) return@forEach
            connectedChains(remaining).forEachIndexed { index, chain ->
                contours += if (index == 0) contour.copy(edges = chain) else PlanContour(newId(), chain)
            }
        }

        return document.copy(contours = contours).withoutUnusedNodes()
    }

    /**
     * Inserts a node on [edgeId] and replaces the edge with two independent edges.
     *
     * The node is placed at [at] projected onto the wall, so the shape does not change. The point
     * stays where the user touched, even on a grid-aligned wall, so its coordinate may be
     * fractional. Snapping it to the grid would move the mark away from the touch and, on a
     * slanted wall, bend the wall itself (ADR 0010).
     *
     * The first half keeps the id of the original edge, the second gets a new one. Both halves
     * lose the dimension: after a split the wall the dimension described no longer exists, and
     * moving the value to one half would silently turn "the wall is 3 m" into "a part of the wall
     * is 3 m". The caller warns the user before calling this function; the split is one undo step,
     * so undo restores the edge together with its dimension.
     *
     * The sheet does not grow, because the new node lies inside the wall. A tap closer than
     * [PlanGeometry.MIN_SEGMENT] to an end is ignored: the stub would be invisible and impossible
     * to pick out of the two walls. Returns the same instance when the edge is unknown or no valid
     * point is left.
     */
    fun splitEdge(
        document: PlanDocument,
        edgeId: String,
        at: PlanPoint,
        newId: () -> String,
    ): PlanDocument {
        val split = edgeSplit(document, edgeId, at) ?: return document
        val edge = split.edge

        val node = PlanNode(id = newId(), x = split.point.x, y = split.point.y)
        val first = edge.copy(endNodeId = node.id, dimension = null)
        val second = PlanEdge(
            id = newId(),
            startNodeId = node.id,
            endNodeId = edge.endNodeId,
        )

        var replaced = false
        val contours = document.contours.map { contour ->
            if (contour.edges.none { it.id == edgeId }) {
                contour
            } else {
                contour.copy(
                    edges = contour.edges.flatMap { current ->
                        if (current.id == edgeId) {
                            replaced = true
                            listOf(first, second)
                        } else {
                            listOf(current)
                        }
                    },
                )
            }
        }
        if (!replaced) return document

        return document.copy(nodes = document.nodes + node, contours = contours)
    }

    /**
     * Whether [splitEdge] would change [document].
     *
     * The screen asks this before warning about a dimension, so a tap too close to an end does not
     * promise a split that is then silently ignored.
     */
    fun canSplitEdge(document: PlanDocument, edgeId: String, at: PlanPoint): Boolean =
        edgeSplit(document, edgeId, at) != null

    /** Where [edgeId] would be split, or `null` when no valid point is left. */
    private fun edgeSplit(
        document: PlanDocument,
        edgeId: String,
        at: PlanPoint,
    ): EdgeSplit? {
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return null
        val start = document.nodes.firstOrNull { it.id == edge.startNodeId } ?: return null
        val end = document.nodes.firstOrNull { it.id == edge.endNodeId } ?: return null
        val startPoint = PlanPoint(start.x, start.y)
        val endPoint = PlanPoint(end.x, end.y)
        if (distance(startPoint, endPoint) < 2 * PlanGeometry.MIN_SEGMENT) return null

        val projected = PlanGeometry.projectOnEdge(document.nodes, edge, at) ?: return null
        if (
            distance(startPoint, projected) < PlanGeometry.MIN_SEGMENT ||
            distance(projected, endPoint) < PlanGeometry.MIN_SEGMENT
        ) {
            return null
        }
        return EdgeSplit(edge, projected)
    }

    private fun distance(first: PlanPoint, second: PlanPoint): Double =
        hypot(second.x - first.x, second.y - first.y)

    /** Edge picked for a split and the point of it where the new node goes. */
    private data class EdgeSplit(val edge: PlanEdge, val point: PlanPoint)

    /**
     * Splits edges, whose order is preserved, into maximal chains: each edge of a chain starts
     * where the previous one ends.
     */
    private fun connectedChains(edges: List<PlanEdge>): List<List<PlanEdge>> {
        val chains = mutableListOf<List<PlanEdge>>()
        var current = mutableListOf<PlanEdge>()
        edges.forEach { edge ->
            val previous = current.lastOrNull()
            if (previous != null && previous.endNodeId != edge.startNodeId) {
                chains += current
                current = mutableListOf()
            }
            current += edge
        }
        if (current.isNotEmpty()) chains += current
        return chains
    }

    fun clearDrawing(document: PlanDocument): PlanDocument =
        document.copy(nodes = emptyList(), contours = emptyList())

    /**
     * Attaches [dimension] to the edge [edgeId]; the geometry is left untouched.
     *
     * Returns the same instance when the edge is unknown or already carries this dimension, which
     * keeps a repeated rangefinder reading out of the undo history.
     */
    fun setDimension(
        document: PlanDocument,
        edgeId: String,
        dimension: PlanDimension,
    ): PlanDocument = document.updateEdge(edgeId) { edge ->
        if (edge.dimension == dimension) edge else edge.copy(dimension = dimension)
    }

    /** Removes the dimension of [edgeId], or returns the same instance when there is none. */
    fun removeDimension(document: PlanDocument, edgeId: String): PlanDocument =
        document.updateEdge(edgeId) { edge ->
            if (edge.dimension == null) edge else edge.copy(dimension = null)
        }

    /**
     * Grows the sheet so that every node stays inside it; see [PlanGeometry.growWorldToFit].
     *
     * Edits keep the invariant themselves. This is the repair path for a document written while
     * the sheet size was still capped, which left nodes outside the sheet. Returns the same
     * instance when the drawing already fits.
     */
    fun fitWorldToDrawing(document: PlanDocument): PlanDocument =
        document.withWorldFittingDrawing()

    /**
     * Grows the sheet so that every node stays inside it, shifting the drawing when a node went
     * left of or above the origin. Returns the same instance when the drawing already fits.
     */
    private fun PlanDocument.withWorldFittingDrawing(): PlanDocument {
        val points = nodes.map { PlanPoint(it.x, it.y) }
        val growth = PlanGeometry.growWorldToFit(world, points) ?: return this
        val moved = if (growth.shift.x == 0.0 && growth.shift.y == 0.0) {
            nodes
        } else {
            nodes.map { it.copy(x = it.x + growth.shift.x, y = it.y + growth.shift.y) }
        }
        return copy(world = growth.size, nodes = moved)
    }

    private fun List<PlanNode>.plusIfAbsent(node: PlanNode): List<PlanNode> =
        if (any { it.id == node.id }) this else this + node

    /**
     * Rebuilds the document with [change] applied to one edge; unchanged instances are preserved so
     * that callers can detect a no-op by identity.
     */
    private fun PlanDocument.updateEdge(
        edgeId: String,
        change: (PlanEdge) -> PlanEdge,
    ): PlanDocument {
        var changed = false
        val contours = contours.map { contour ->
            if (contour.edges.none { it.id == edgeId }) return@map contour
            contour.copy(
                edges = contour.edges.map { edge ->
                    if (edge.id != edgeId) {
                        edge
                    } else {
                        change(edge).also { updated -> if (updated !== edge) changed = true }
                    }
                },
            )
        }
        if (!changed) return this
        return copy(contours = contours)
    }

    private fun PlanDocument.withoutUnusedNodes(): PlanDocument {
        val used = contours
            .flatMap { contour -> contour.edges }
            .flatMap { edge -> listOf(edge.startNodeId, edge.endNodeId) }
            .toSet()
        return copy(nodes = nodes.filter { it.id in used })
    }

    /**
     * Reattaches every edge of [movedId] to [targetId] and drops the empty node.
     *
     * Returns `null` when both nodes already have a wall to the same neighbour: after welding those
     * walls would be duplicates, and silently deleting one would lose its dimension.
     */
    private fun PlanDocument.mergeNodeInto(movedId: String, targetId: String): PlanDocument? {
        if (!PlanGeometry.canWeldNodes(edges, movedId, targetId)) return null

        val merged = contours.map { contour ->
            contour.copy(
                edges = contour.edges.map { edge ->
                    edge.copy(
                        startNodeId = if (edge.startNodeId == movedId) targetId else edge.startNodeId,
                        endNodeId = if (edge.endNodeId == movedId) targetId else edge.endNodeId,
                    )
                },
            )
        }
        return copy(contours = merged).withoutUnusedNodes()
    }
}
