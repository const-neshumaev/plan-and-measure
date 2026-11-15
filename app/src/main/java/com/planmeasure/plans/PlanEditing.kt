package com.planmeasure.plans

/**
 * Applies drawing gestures to a plan.
 *
 * One gesture adds one edge. The edge continues the contour that ends at its start node,
 * otherwise it starts a new contour. Ending an edge on the first node of a contour closes it.
 */
object PlanEditing {

    /**
     * Adds a segment from [from] to [to] snapped by [PlanGeometry].
     *
     * [newId] is called for every node, edge and contour that has to be created. Returns the
     * same instance when the gesture produces no usable segment.
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

        return document.copy(nodes = nodes, contours = contours)
    }

    /** Removes the last drawn edge and any node that no longer belongs to an edge. */
    fun undoLastSegment(document: PlanDocument): PlanDocument {
        val contourIndex = document.contours.indexOfLast { it.edges.isNotEmpty() }
        if (contourIndex < 0) return document

        val contour = document.contours[contourIndex]
        val edges = contour.edges.dropLast(1)
        val contours = document.contours.toMutableList()
        if (edges.isEmpty()) {
            contours.removeAt(contourIndex)
        } else {
            contours[contourIndex] = contour.copy(edges = edges)
        }

        return document.copy(contours = contours).withoutUnusedNodes()
    }

    fun clearDrawing(document: PlanDocument): PlanDocument =
        document.copy(nodes = emptyList(), contours = emptyList())

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
