package com.planmeasure.plans

/**
 * Applies drawing and editing gestures to a plan.
 *
 * One drawing gesture adds one edge. The edge continues the contour that ends at its start node,
 * otherwise it starts a new contour. Ending an edge on the first node of a contour closes it.
 * Editing moves a node, which drags every edge that touches it, slides one edge as a whole, or
 * removes one edge. Anything that leaves the sheet grows it, see [PlanGeometry.growWorldToFit].
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
     * A node is shared by every edge that touches it, so the whole shape follows it. A node pulled
     * past the border grows the sheet. Returns the same instance when the node is unknown or the
     * target would collapse an incident edge.
     */
    fun moveNode(document: PlanDocument, nodeId: String, to: PlanPoint): PlanDocument {
        val node = document.nodes.firstOrNull { it.id == nodeId } ?: return document
        val target = PlanGeometry.snapNodeMove(document.nodes, document.edges, node, to)
            ?: return document
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

    private fun PlanDocument.withoutUnusedNodes(): PlanDocument {
        val used = contours
            .flatMap { contour -> contour.edges }
            .flatMap { edge -> listOf(edge.startNodeId, edge.endNodeId) }
            .toSet()
        return copy(nodes = nodes.filter { it.id in used })
    }
}
