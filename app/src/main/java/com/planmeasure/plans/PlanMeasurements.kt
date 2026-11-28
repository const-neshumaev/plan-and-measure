package com.planmeasure.plans

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Measures a drawn plan: areas of closed contours, their perimeters and the total length of chosen
 * edges (ADR 0020).
 *
 * Everything is derived from the current geometry and [PlanDocument.mmPerUnit]; nothing here writes
 * to the document. A value that disagrees with an entered dimension is not recomputed in another
 * way: the dimensions already shaped the drawing (ADR 0012), so the area is what the user sees and
 * [PlanContourMeasurement.mismatchedEdges] says how much of it rests on the sketch.
 *
 * A closed loop is a polygon. Loops are derived from the graph of edges, not only from [PlanContour]
 * chains: a rectangle drawn as two chains from one corner to another is still one loop. Nested loops
 * inside the selected one are cut-outs; only loops that lie strictly inside without touching are
 * subtracted, because a partial overlap would need boolean polygon geometry and a result that is not
 * obvious on screen.
 */
object PlanMeasurements {

    /** Tolerance of the geometric predicates, far below the drawing's resolution. */
    private const val EPSILON = 1e-9

    /** One cut-out of an area calculation. */
    data class PlanHoleArea(
        val contourId: String,
        val areaSquareMillimeters: Double,
        /** Edges of the cut-out, used by the small sketch in the details dialog. */
        val edgeIds: List<String> = emptyList(),
    )

    /** Measurements of one closed contour, including the cut-outs found inside it. */
    data class PlanContourMeasurement(
        val contourId: String,
        val grossAreaSquareMillimeters: Double,
        val holes: List<PlanHoleArea>,
        val netAreaSquareMillimeters: Double,
        val perimeterMillimeters: Double,
        val measuredEdges: Int,
        val edgeCount: Int,
        val mismatchedEdges: Int,
    )

    /** Measurements of a set of selected edges. */
    data class PlanLengthMeasurement(
        val edgeIds: List<String>,
        val lengthMillimeters: Double,
        val edgeCount: Int,
        val mismatchedEdges: Int,
    )

    /**
     * A simple closed loop of edges found in the drawing.
     *
     * A loop is not the same as a [PlanContour]: the user may draw a rectangle as two chains from
     * one corner to another, and the cycle between them is still what the eye sees. [key] identifies
     * the set of edges, so the same loop found from different starting edges is counted once.
     */
    data class PlanLoop(
        val edgeIds: List<String>,
        val points: List<PlanPoint>,
        val areaSquareUnits: Double,
    ) {
        val key: String get() = loopKey(edgeIds)
    }

    /** What a saved calculation currently yields. */
    data class PlanCalculationResult(
        val contour: PlanContourMeasurement? = null,
        val length: PlanLengthMeasurement? = null,
        val valid: Boolean,
    ) {
        val mismatched: Boolean
            get() = (contour?.mismatchedEdges ?: 0) > 0 || (length?.mismatchedEdges ?: 0) > 0
    }

    /**
     * Ordered vertices of a closed [contourId], or `null` when the contour is open, has fewer than
     * three edges, lacks a node or intersects itself.
     *
     * Self-intersection is rejected rather than measured: the shoelace formula would return an
     * algebraic area with cancelled lobes, which is not a number the user can apply to materials.
     */
    fun contourPoints(document: PlanDocument, contourId: String): List<PlanPoint>? {
        val contour = document.contours.firstOrNull { it.id == contourId } ?: return null
        if (contour.edges.size < 3) return null
        val nodes = document.nodes.associateBy { it.id }
        val points = ArrayList<PlanPoint>(contour.edges.size)
        val startId = contour.edges.first().startNodeId
        var currentId = startId
        contour.edges.forEach { edge ->
            if (edge.startNodeId != currentId) return null
            val node = nodes[edge.startNodeId] ?: return null
            points += PlanPoint(node.x, node.y)
            currentId = edge.endNodeId
        }
        if (currentId != startId) return null
        return points.takeIf { isSimple(it) }
    }

    /**
     * Smallest closed contour containing [point], or `null` when the tap is outside every contour.
     *
     * The smallest one is returned so that a tap inside a window selects the window, not the wall
     * around it.
     */
    fun contourAt(document: PlanDocument, point: PlanPoint): PlanContour? =
        document.contours
            .asSequence()
            .filter { it.isClosed }
            .mapNotNull { contour ->
                val points = contourPoints(document, contour.id) ?: return@mapNotNull null
                if (!contains(points, point, includeBoundary = true)) return@mapNotNull null
                contour to polygonArea(points)
            }
            .minByOrNull { it.second }
            ?.first

    /**
     * Smallest simple loop containing [point], or `null` when the tap is outside every loop.
     *
     * The loop is derived from the graph of edges, not from [PlanContour] chains, so a shape drawn
     * as several chains is still selectable. The smallest loop wins: a tap inside a window selects
     * the window, not the wall around it.
     */
    fun loopAt(document: PlanDocument, point: PlanPoint): PlanLoop? =
        loops(document)
            .asSequence()
            .filter { contains(it.points, point, includeBoundary = true) }
            .minByOrNull { it.areaSquareUnits }

    /**
     * Every simple loop of the drawing.
     *
     * One loop is found for each edge by the shortest path between its ends without that edge. The
     * technique is enough for the sketch-like plans this app draws: rooms, nested outlines and
     * shapes assembled from several chains. Duplicate loops are removed by [PlanLoop.key].
     */
    fun loops(document: PlanDocument): List<PlanLoop> {
        val nodesById = document.nodes.associateBy { it.id }
        val edges = document.edges.filter { edge ->
            if (edge.startNodeId == edge.endNodeId) return@filter false
            val start = nodesById[edge.startNodeId] ?: return@filter false
            val end = nodesById[edge.endNodeId] ?: return@filter false
            start.x != end.x || start.y != end.y
        }
        if (edges.isEmpty()) return emptyList()

        val adjacency = mutableMapOf<String, MutableList<PlanEdge>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.startNodeId) { mutableListOf() } += edge
            adjacency.getOrPut(edge.endNodeId) { mutableListOf() } += edge
        }

        val seen = mutableSetOf<String>()
        val result = mutableListOf<PlanLoop>()
        edges.forEach { excluded ->
            val path = shortestPath(
                adjacency = adjacency,
                startNodeId = excluded.endNodeId,
                endNodeId = excluded.startNodeId,
                excludedEdgeId = excluded.id,
            ) ?: return@forEach
            val loopEdgeIds = listOf(excluded.id) + path
            if (!seen.add(loopKey(loopEdgeIds))) return@forEach
            val points = loopPoints(document, loopEdgeIds) ?: return@forEach
            val area = polygonArea(points)
            if (area <= 0.0) return@forEach
            result += PlanLoop(loopEdgeIds, points, area)
        }
        return result
    }

    /**
     * Ordered polygon of the loop made by [edgeIds], or `null` when the edges do not form exactly
     * one simple closed cycle.
     *
     * The stored order of the ids does not matter: the cycle is walked from the first edge, so a
     * calculation survives a split or a reordered selection.
     */
    fun loopPoints(document: PlanDocument, edgeIds: Collection<String>): List<PlanPoint>? {
        val requested = edgeIds.distinct()
        if (requested.size < 3) return null
        val edgesById = document.edges.associateBy { it.id }
        val remaining = requested.mapNotNull { edgesById[it] }.toMutableList()
        if (remaining.size != requested.size) return null

        val first = remaining.removeAt(0)
        val nodes = document.nodes.associateBy { it.id }
        val nodeIds = mutableListOf(first.startNodeId, first.endNodeId)
        var current = first.endNodeId
        while (remaining.isNotEmpty()) {
            val incident = remaining.filter { edge ->
                edge.startNodeId == current || edge.endNodeId == current
            }
            if (incident.size != 1) return null
            val edge = incident.single()
            remaining.remove(edge)
            current = if (edge.startNodeId == current) edge.endNodeId else edge.startNodeId
            nodeIds += current
        }
        if (current != first.startNodeId) return null

        val points = nodeIds.dropLast(1).map { id ->
            val node = nodes[id] ?: return null
            PlanPoint(node.x, node.y)
        }
        return points.takeIf { it.size >= 3 && isSimple(it) }
    }

    /**
     * Direct cut-outs of the loop made by [edgeIds]: loops lying strictly inside it and not inside
     * another cut-out. Used by the editor fill and by [measureArea].
     */
    fun holeLoops(document: PlanDocument, edgeIds: Collection<String>): List<PlanLoop> {
        val requested = edgeIds.distinct()
        val points = loopPoints(document, requested) ?: return emptyList()
        val selectedKey = loopKey(requested)
        val elseLoops = loops(document).filter { it.key != selectedKey }
        val candidates = elseLoops.filter { containsPolygon(points, it.points) }
        return candidates.filter { candidate ->
            candidates.none { other ->
                other.key != candidate.key && containsPolygon(other.points, candidate.points)
            }
        }
    }

    /**
     * Measures the loop made by [edgeIds] in real units, or `null` while the plan has no scale or
     * the edges do not form one simple closed cycle.
     *
     * Cut-outs are other loops lying strictly inside this one; the same direct-child rule as in
     * [measureContour] keeps an island inside a window from being subtracted twice.
     */
    fun measureArea(
        document: PlanDocument,
        edgeIds: Collection<String>,
        subtractHoles: Boolean,
    ): PlanContourMeasurement? {
        val scale = millimetersPerUnit(document) ?: return null
        val requested = edgeIds.distinct()
        val points = loopPoints(document, requested) ?: return null
        val selectedKey = loopKey(requested)
        val holes = holeLoops(document, requested)

        val gross = polygonArea(points) * scale * scale
        if (gross <= 0.0) return null
        val holeAreas = holes.map {
            PlanHoleArea(it.key, it.areaSquareUnits * scale * scale, it.edgeIds)
        }
        val net = if (subtractHoles) {
            gross - holeAreas.sumOf { it.areaSquareMillimeters }
        } else {
            gross
        }
        val perimeter = polygonLength(points) * scale

        val edgesById = document.edges.associateBy { it.id }
        val selectedEdges = requested.mapNotNull { edgesById[it] }
        if (selectedEdges.size != requested.size) return null
        val holeEdgeIds = holes.flatMap { it.edgeIds }.toSet()
        val mismatchedIds = PlanScale.mismatchedEdgeIds(document)
        val selectedIds = requested.toSet()
        return PlanContourMeasurement(
            contourId = selectedKey,
            grossAreaSquareMillimeters = gross,
            holes = holeAreas,
            netAreaSquareMillimeters = net,
            perimeterMillimeters = perimeter,
            measuredEdges = selectedEdges.count { it.dimension != null },
            edgeCount = selectedEdges.size,
            mismatchedEdges = (selectedIds + holeEdgeIds).count { it in mismatchedIds },
        )
    }

    /**
     * Closed contours lying directly inside [contourId] and therefore treated as cut-outs.
     *
     * Direct means "not inside another cut-out": a contour inside a window is not subtracted from
     * the wall a second time. Only fully contained contours count; touching or crossing outlines
     * are ignored.
     */
    fun holeIds(document: PlanDocument, contourId: String): List<String> {
        val outer = contourPoints(document, contourId) ?: return emptyList()
        val candidates = document.contours
            .asSequence()
            .filter { it.id != contourId && it.isClosed }
            .mapNotNull { contour ->
                val points = contourPoints(document, contour.id) ?: return@mapNotNull null
                contour.id to points
            }
            .filter { (_, points) -> containsPolygon(outer, points) }
            .toList()
        return candidates
            .filter { (id, points) ->
                candidates.none { (otherId, otherPoints) ->
                    otherId != id && containsPolygon(otherPoints, points)
                }
            }
            .map { it.first }
    }

    /**
     * Measures [contourId] in real units, or `null` while the plan has no scale or the contour is
     * not a simple closed polygon.
     *
     * [subtractHoles] controls only the net value; the cut-outs are reported either way so the
     * interface can show both the gross outline and what was removed.
     */
    fun measureContour(
        document: PlanDocument,
        contourId: String,
        subtractHoles: Boolean,
    ): PlanContourMeasurement? {
        val scale = millimetersPerUnit(document) ?: return null
        val contour = document.contours.firstOrNull { it.id == contourId } ?: return null
        val points = contourPoints(document, contourId) ?: return null

        val gross = polygonArea(points) * scale * scale
        val holeIds = holeIds(document, contourId)
        val holes = holeIds.mapNotNull { id ->
            val holePoints = contourPoints(document, id) ?: return@mapNotNull null
            val holeEdges = document.contours.firstOrNull { it.id == id }
                ?.edges
                ?.map { it.id }
                .orEmpty()
            PlanHoleArea(id, polygonArea(holePoints) * scale * scale, holeEdges)
        }
        val net = if (subtractHoles) {
            gross - holes.sumOf { it.areaSquareMillimeters }
        } else {
            gross
        }
        val perimeter = polygonLength(points) * scale

        val mismatchedIds = PlanScale.mismatchedEdgeIds(document)
        val contourEdges = contour.edges
        val holeEdges = holeIds.flatMap { id ->
            document.contours.firstOrNull { it.id == id }?.edges.orEmpty()
        }
        return PlanContourMeasurement(
            contourId = contourId,
            grossAreaSquareMillimeters = gross,
            holes = holes,
            netAreaSquareMillimeters = net,
            perimeterMillimeters = perimeter,
            measuredEdges = contourEdges.count { it.dimension != null },
            edgeCount = contourEdges.size,
            mismatchedEdges = (contourEdges + holeEdges).count { it.id in mismatchedIds },
        )
    }

    /**
     * Total length of [edgeIds] in real units, or `null` while the plan has no scale, the set is
     * empty or one of the edges no longer exists.
     */
    fun measureLength(
        document: PlanDocument,
        edgeIds: Collection<String>,
    ): PlanLengthMeasurement? {
        val scale = millimetersPerUnit(document) ?: return null
        val requested = edgeIds.distinct()
        if (requested.isEmpty()) return null
        val edgesById = document.edges.associateBy { it.id }
        val nodesById = document.nodes.associateBy { it.id }
        val selected = requested.map { edgesById[it] ?: return null }

        var length = 0.0
        selected.forEach { edge ->
            val start = nodesById[edge.startNodeId] ?: return null
            val end = nodesById[edge.endNodeId] ?: return null
            length += hypot(end.x - start.x, end.y - start.y)
        }
        val mismatchedIds = PlanScale.mismatchedEdgeIds(document)
        return PlanLengthMeasurement(
            edgeIds = selected.map { it.id },
            lengthMillimeters = length * scale,
            edgeCount = selected.size,
            mismatchedEdges = selected.count { it.id in mismatchedIds },
        )
    }

    /** Current value of a saved [calculation], see [PlanCalculation]. */
    fun evaluate(
        document: PlanDocument,
        calculation: PlanCalculation,
    ): PlanCalculationResult = when (calculation.kind) {
        PlanCalculationKind.AREA -> {
            // New calculations point at the loop's edges, so a shape drawn as several chains is
            // still measurable. The contour id is kept for documents written before that change.
            val measurement = if (calculation.edgeIds.isNotEmpty()) {
                measureArea(document, calculation.edgeIds, calculation.subtractHoles)
            } else {
                calculation.contourId?.let { contourId ->
                    measureContour(document, contourId, calculation.subtractHoles)
                }
            }
            PlanCalculationResult(
                contour = measurement,
                valid = measurement != null && measurement.netAreaSquareMillimeters >= 0.0,
            )
        }
        PlanCalculationKind.LENGTH -> {
            val measurement = measureLength(document, calculation.edgeIds)
            PlanCalculationResult(
                length = measurement,
                valid = measurement != null,
            )
        }
    }

    private fun millimetersPerUnit(document: PlanDocument): Double? =
        document.mmPerUnit?.takeIf { it > 0.0 }

    /** Shortest path between two nodes without [excludedEdgeId], as ordered edge ids. */
    private fun shortestPath(
        adjacency: Map<String, List<PlanEdge>>,
        startNodeId: String,
        endNodeId: String,
        excludedEdgeId: String,
    ): List<String>? {
        if (startNodeId == endNodeId) return null
        val visited = mutableSetOf(startNodeId)
        val parentEdge = mutableMapOf<String, PlanEdge>()
        val parentNode = mutableMapOf<String, String>()
        val queue = ArrayDeque<String>()
        queue.addLast(startNodeId)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node == endNodeId) break
            adjacency[node].orEmpty().forEach { edge ->
                if (edge.id == excludedEdgeId) return@forEach
                val other = edge.otherNode(node) ?: return@forEach
                if (!visited.add(other)) return@forEach
                parentEdge[other] = edge
                parentNode[other] = node
                queue.addLast(other)
            }
        }
        if (endNodeId !in visited) return null

        val edgeIds = mutableListOf<String>()
        var current = endNodeId
        while (current != startNodeId) {
            val edge = parentEdge[current] ?: return null
            edgeIds += edge.id
            current = parentNode[current] ?: return null
        }
        edgeIds.reverse()
        return edgeIds
    }

    private fun PlanEdge.otherNode(nodeId: String): String? = when (nodeId) {
        startNodeId -> endNodeId
        endNodeId -> startNodeId
        else -> null
    }

    /** Shoelace area of an ordered polygon; the sign of the winding does not matter. */
    private fun polygonArea(points: List<PlanPoint>): Double {
        var sum = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            sum += current.x * next.y - next.x * current.y
        }
        return abs(sum) / 2.0
    }

    /** Length of the closed outline through [points]. */
    private fun polygonLength(points: List<PlanPoint>): Double {
        var sum = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            sum += hypot(next.x - current.x, next.y - current.y)
        }
        return sum
    }

    private fun isSimple(points: List<PlanPoint>): Boolean {
        if (points.size < 3) return false
        val size = points.size
        for (first in 0 until size) {
            val firstNext = (first + 1) % size
            for (second in first + 1 until size) {
                val secondNext = (second + 1) % size
                // Neighbouring edges share a vertex by design; only other pairs can cross.
                if (second == firstNext || (first == 0 && second == size - 1)) continue
                if (segmentsIntersect(points[first], points[firstNext], points[second], points[secondNext])) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Whether [inner] lies strictly inside [outer] without touching it.
     *
     * Both conditions are needed: all vertices inside is not enough for a concave outer polygon,
     * because an inner edge can leave and re-enter while its vertices stay inside.
     */
    private fun containsPolygon(outer: List<PlanPoint>, inner: List<PlanPoint>): Boolean {
        if (inner.isEmpty()) return false
        if (inner.any { !contains(outer, it, includeBoundary = false) }) return false
        for (outerIndex in outer.indices) {
            val outerStart = outer[outerIndex]
            val outerEnd = outer[(outerIndex + 1) % outer.size]
            for (innerIndex in inner.indices) {
                val innerStart = inner[innerIndex]
                val innerEnd = inner[(innerIndex + 1) % inner.size]
                if (segmentsIntersect(outerStart, outerEnd, innerStart, innerEnd)) return false
            }
        }
        return true
    }

    /** Ray casting; a point on the boundary counts only when [includeBoundary] is set. */
    private fun contains(points: List<PlanPoint>, point: PlanPoint, includeBoundary: Boolean): Boolean {
        for (index in points.indices) {
            if (pointOnSegment(points[index], points[(index + 1) % points.size], point)) {
                return includeBoundary
            }
        }
        var inside = false
        var previous = points.lastIndex
        for (index in points.indices) {
            val current = points[index]
            val before = points[previous]
            if ((current.y > point.y) != (before.y > point.y)) {
                val crossingX = (before.x - current.x) * (point.y - current.y) /
                    (before.y - current.y) + current.x
                if (point.x < crossingX) inside = !inside
            }
            previous = index
        }
        return inside
    }

    private fun segmentsIntersect(
        firstStart: PlanPoint,
        firstEnd: PlanPoint,
        secondStart: PlanPoint,
        secondEnd: PlanPoint,
    ): Boolean {
        val first = orientation(firstStart, firstEnd, secondStart)
        val second = orientation(firstStart, firstEnd, secondEnd)
        val third = orientation(secondStart, secondEnd, firstStart)
        val fourth = orientation(secondStart, secondEnd, firstEnd)

        val firstStraddles = (first > EPSILON && second < -EPSILON) ||
            (first < -EPSILON && second > EPSILON)
        val secondStraddles = (third > EPSILON && fourth < -EPSILON) ||
            (third < -EPSILON && fourth > EPSILON)
        if (firstStraddles && secondStraddles) return true

        return (abs(first) <= EPSILON && pointOnSegment(firstStart, firstEnd, secondStart)) ||
            (abs(second) <= EPSILON && pointOnSegment(firstStart, firstEnd, secondEnd)) ||
            (abs(third) <= EPSILON && pointOnSegment(secondStart, secondEnd, firstStart)) ||
            (abs(fourth) <= EPSILON && pointOnSegment(secondStart, secondEnd, firstEnd))
    }

    private fun orientation(start: PlanPoint, end: PlanPoint, point: PlanPoint): Double =
        (end.x - start.x) * (point.y - start.y) - (end.y - start.y) * (point.x - start.x)

    private fun pointOnSegment(start: PlanPoint, end: PlanPoint, point: PlanPoint): Boolean {
        if (abs(orientation(start, end, point)) > EPSILON) return false
        val minX = minOf(start.x, end.x) - EPSILON
        val maxX = maxOf(start.x, end.x) + EPSILON
        val minY = minOf(start.y, end.y) - EPSILON
        val maxY = maxOf(start.y, end.y) + EPSILON
        return point.x in minX..maxX && point.y in minY..maxY
    }
}

/** Identity of a loop: the same set of edges is the same loop, whatever order it was found in. */
private fun loopKey(edgeIds: Collection<String>): String =
    edgeIds.sorted().joinToString("|")
