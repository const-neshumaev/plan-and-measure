package com.planmeasure.plans

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ties the dimensions of a plan to its geometry (ADR 0012).
 *
 * A plan is a sketch: the angles and the connections are what the user drew, and a dimension does
 * not change them. Entering a dimension only declares how long a wall really is:
 *
 * - the first dimension of a plan sets the scale, millimeters per grid unit, and moves nothing;
 * - every later dimension becomes a length the drawing has to keep, and the walls without a
 *   dimension stretch or shrink to accommodate it, keeping their direction;
 * - a dimension the fixed angles cannot accommodate is kept as a label only and marked as not
 *   matching the drawing: a closed contour of `m` edges has `m - 2` free lengths per independent
 *   loop, so a triangle can show one dimension and a rectangle two.
 *
 * Dimensions are applied when they are entered, not enforced afterwards. Reshaping the plan stays
 * free: a drag changes angles and lengths, and a dimension that no longer fits is marked instead of
 * pulling the finger back. The next entered dimension fits the drawing again.
 */
object PlanScale {

    /** Share of the entered length the drawn wall may differ by before it is marked. */
    const val MISMATCH_RATIO = 0.01

    /**
     * Weight of a constraint row against the objective. Large enough to keep a dimension exact to
     * the drawing's precision, small enough for the linear system to stay well conditioned.
     */
    private const val CONSTRAINT_WEIGHT = 1e4

    /** Relative slack that lets a constraint count as satisfied after the numerical solve. */
    private const val CONSTRAINT_TOLERANCE = 1e-4

    /** Shortest length the fit may produce; below it the edge could not be picked on screen. */
    private const val MIN_FIT_LENGTH = 0.1

    /**
     * Coordinate change below this is no change, so a fit that moves nothing does not rewrite the
     * drawing. It is far below the drawing's resolution, and the solve keeps the target lengths to
     * about the same relative precision.
     */
    private const val POSITION_TOLERANCE = 1e-6

    /** Millimeters per grid unit, or `null` while the plan is still an unscaled sketch. */
    fun millimetersPerUnit(document: PlanDocument): Double? = document.mmPerUnit

    /**
     * Length of the wall [edgeId] as it is drawn, in millimeters, or `null` while the plan has no
     * scale or the wall cannot be measured.
     */
    fun drawnMillimeters(document: PlanDocument, edgeId: String): Double? {
        val scale = document.mmPerUnit ?: return null
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return null
        return edgeLength(document, edge)?.let { it * scale }
    }

    /** Whether the drawn wall differs from its dimension by more than [MISMATCH_RATIO]. */
    fun isMismatched(document: PlanDocument, edgeId: String): Boolean {
        val scale = document.mmPerUnit ?: return false
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return false
        val dimension = edge.dimension ?: return false
        val length = edgeLength(document, edge) ?: return false
        return differs(dimension, length, scale)
    }

    /**
     * How far the drawn wall is from its dimension, in millimeters, or `null` when the plan has no
     * scale, the wall carries no readable dimension or the wall cannot be measured.
     *
     * The value is absolute: the card names the wall and the user sees the `≈` mark on it, so the
     * side of the disagreement carries no information (ADR 0013).
     */
    fun deviationMillimeters(document: PlanDocument, edgeId: String): Double? {
        val scale = document.mmPerUnit ?: return null
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return null
        val value = edge.dimension?.valueMm?.toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        val length = edgeLength(document, edge) ?: return null
        return abs(length * scale - value)
    }

    /**
     * Every wall whose dimension does not match the drawing.
     *
     * Both cases end up here: a dimension the fixed angles could not accommodate when it was
     * entered, and a dimension the user broke by reshaping the plan afterwards. The state is
     * derived, not stored, so it disappears as soon as the drawing agrees again.
     */
    fun mismatchedEdgeIds(document: PlanDocument): Set<String> {
        val scale = document.mmPerUnit ?: return emptySet()
        val nodes = document.nodes.associateBy { it.id }
        return document.edges.mapNotNull { edge ->
            val dimension = edge.dimension ?: return@mapNotNull null
            val start = nodes[edge.startNodeId] ?: return@mapNotNull null
            val end = nodes[edge.endNodeId] ?: return@mapNotNull null
            val length = hypot(end.x - start.x, end.y - start.y)
            if (length > 0.0 && differs(dimension, length, scale)) edge.id else null
        }.toSet()
    }

    /**
     * Stores [dimension] on [edgeId] and refits the drawing.
     *
     * A plan without a scale gets one from this dimension and keeps its geometry; later dimensions
     * are converted with that scale. Re-measuring the wall that set the scale moves the scale
     * itself, because the user corrected the very value everything else is derived from. The sheet
     * follows the reshaped drawing: it grows when a wall leaves it and is trimmed back when the
     * drawing becomes smaller than the sheet it once needed. Returns the same instance when the edge
     * is unknown or already carries this dimension.
     */
    fun applyDimension(
        document: PlanDocument,
        edgeId: String,
        dimension: PlanDimension,
    ): PlanDocument {
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return document
        if (edge.dimension == dimension) return document

        val updated = document.replaceEdge(edgeId) { it.copy(dimension = dimension) }
        val setsScale = updated.mmPerUnit == null || updated.scaleEdgeId == edgeId
        val scale = if (setsScale) {
            scaleOfWall(updated, edgeId) ?: updated.mmPerUnit
        } else {
            updated.mmPerUnit
        }
        val scaled = updated.copy(
            mmPerUnit = scale,
            scaleEdgeId = if (setsScale && scale != null) edgeId else updated.scaleEdgeId,
        )
        return refit(scaled).withWorldTrimmedToDrawing()
    }

    /**
     * Drops a scale the document can no longer justify.
     *
     * Called after every edit that can remove a dimension: a plan without dimensions is a plain
     * sketch again, and a scale whose wall lost its dimension keeps the value but forgets the wall.
     */
    fun normalize(document: PlanDocument): PlanDocument {
        val dimensioned = document.edges.filter { it.dimension != null }
        val danglingScaleEdge = document.scaleEdgeId != null &&
            dimensioned.none { it.id == document.scaleEdgeId }
        return when {
            dimensioned.isEmpty() && (document.mmPerUnit != null || document.scaleEdgeId != null) ->
                document.copy(mmPerUnit = null, scaleEdgeId = null)
            danglingScaleEdge -> document.copy(scaleEdgeId = null)
            else -> document
        }
    }

    /** Millimeters per unit that the dimension of [edgeId] implies, or `null` when unreadable. */
    private fun scaleOfWall(document: PlanDocument, edgeId: String): Double? {
        val edge = document.edges.firstOrNull { it.id == edgeId } ?: return null
        val value = edge.dimension?.valueMm?.toDoubleOrNull() ?: return null
        val length = edgeLength(document, edge) ?: return null
        return if (value > 0.0 && length > 0.0) value / length else null
    }

    /** Whether [drawnLength] in [scale] already fails to express the dimension's value. */
    private fun differs(dimension: PlanDimension, drawnLength: Double, scale: Double): Boolean {
        val value = dimension.valueMm.toDoubleOrNull() ?: return false
        return value > 0.0 && abs(drawnLength * scale - value) > value * MISMATCH_RATIO
    }

    private fun edgeLength(document: PlanDocument, edge: PlanEdge): Double? {
        val nodes = document.nodes.associateBy { it.id }
        val start = nodes[edge.startNodeId] ?: return null
        val end = nodes[edge.endNodeId] ?: return null
        val length = hypot(end.x - start.x, end.y - start.y)
        return if (length > 0.0) length else null
    }

    /**
     * Reshapes the drawing so that as many dimensions as possible hold.
     *
     * Dimensions are tried oldest first: a wall the user measured earlier outranks a later one,
     * because its length is already part of the drawing everything else was fitted to. A dimension
     * that cannot be added without breaking the accepted ones is skipped, and the drawing is left
     * as it was for it.
     */
    private fun refit(document: PlanDocument): PlanDocument {
        val scale = document.mmPerUnit ?: return document
        if (scale <= 0.0) return document
        val graph = buildGraph(document) ?: return document
        val targets = dimensionConstraints(document, graph, scale)
        if (targets.isEmpty()) return document

        val start = DoubleArray(graph.walls.size) { graph.walls[it].length }
        val closures = graph.closureConstraints()
        val accepted = mutableListOf<Constraint>()
        targets.forEach { target ->
            val applied = accepted + target
            val lengths = solveLengths(graph.walls.size, closures + applied, start) ?: return@forEach
            if (isSatisfied(graph, lengths, applied)) accepted += target
        }

        val lengths = solveLengths(graph.walls.size, closures + accepted, start) ?: return document
        return document.withLengths(graph, lengths)
    }

    /** Dimensions in the order they were entered, converted to drawing units. */
    private fun dimensionConstraints(
        document: PlanDocument,
        graph: Graph,
        scale: Double,
    ): List<Constraint> {
        val size = graph.walls.size
        return document.edges
            .mapIndexedNotNull { index, edge ->
                val dimension = edge.dimension ?: return@mapIndexedNotNull null
                val wallIndex = graph.wallIndexByEdgeId[edge.id] ?: return@mapIndexedNotNull null
                val value = dimension.valueMm.toDoubleOrNull() ?: return@mapIndexedNotNull null
                if (value <= 0.0) return@mapIndexedNotNull null
                OrderedDimension(
                    updatedAt = dimension.updatedAt,
                    index = index,
                    wallIndex = wallIndex,
                    target = value / scale,
                )
            }
            .sortedWith(compareBy({ it.updatedAt }, { it.index }))
            .map { ordered ->
                Constraint(
                    row = DoubleArray(size).also { it[ordered.wallIndex] = 1.0 },
                    rhs = ordered.target,
                )
            }
    }

    /**
     * Lengths that meet [constraints] with the least change from [start].
     *
     * Constraints enter the least-squares cost with a large weight instead of being enforced
     * exactly. The system stays square and always solvable, and a contradictory set degrades into
     * the closest fit, which the caller then rejects, instead of failing here.
     */
    private fun solveLengths(
        size: Int,
        constraints: List<Constraint>,
        start: DoubleArray,
    ): DoubleArray? {
        val matrix = Array(size) { row ->
            DoubleArray(size) { column -> if (row == column) 1.0 else 0.0 }
        }
        val rhs = start.copyOf()
        val weight = CONSTRAINT_WEIGHT * CONSTRAINT_WEIGHT
        constraints.forEach { constraint ->
            val coefficients = constraint.row
            for (first in 0 until size) {
                if (coefficients[first] == 0.0) continue
                rhs[first] += weight * coefficients[first] * constraint.rhs
                for (second in 0 until size) {
                    matrix[first][second] += weight * coefficients[first] * coefficients[second]
                }
            }
        }
        return solveDense(matrix, rhs)
    }

    /** Gaussian elimination with partial pivoting; `null` when the matrix is singular. */
    private fun solveDense(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val size = rhs.size
        val a = Array(size) { matrix[it].copyOf() }
        val b = rhs.copyOf()
        for (column in 0 until size) {
            var pivot = column
            for (row in column + 1 until size) {
                if (abs(a[row][column]) > abs(a[pivot][column])) pivot = row
            }
            if (abs(a[pivot][column]) < 1e-12) return null
            if (pivot != column) {
                val swappedRow = a[pivot]
                a[pivot] = a[column]
                a[column] = swappedRow
                val swappedValue = b[pivot]
                b[pivot] = b[column]
                b[column] = swappedValue
            }
            for (row in column + 1 until size) {
                val factor = a[row][column] / a[column][column]
                if (factor == 0.0) continue
                for (index in column until size) a[row][index] -= factor * a[column][index]
                b[row] -= factor * b[column]
            }
        }
        val result = DoubleArray(size)
        for (row in size - 1 downTo 0) {
            var sum = b[row]
            for (column in row + 1 until size) sum -= a[row][column] * result[column]
            result[row] = sum / a[row][row]
        }
        return if (result.all { it.isFinite() }) result else null
    }

    private fun isSatisfied(
        graph: Graph,
        lengths: DoubleArray,
        constraints: List<Constraint>,
    ): Boolean {
        if (lengths.any { !it.isFinite() || it < MIN_FIT_LENGTH }) return false
        val satisfied = constraints.all { constraint ->
            val actual = constraint.row.indices.sumOf { index ->
                constraint.row[index] * lengths[index]
            }
            abs(actual - constraint.rhs) <= CONSTRAINT_TOLERANCE * (1.0 + abs(constraint.rhs))
        }
        if (!satisfied) return false
        return graph.positions(lengths).all {
            abs(it.x) <= PlanWorld.MAX_COORDINATE && abs(it.y) <= PlanWorld.MAX_COORDINATE
        }
    }

    /**
     * Rebuilds the nodes from [lengths], keeping the top-left corner of the drawing in place, and
     * grows the sheet if the drawing no longer fits it.
     */
    private fun PlanDocument.withLengths(graph: Graph, lengths: DoubleArray): PlanDocument {
        val positions = graph.positions(lengths)
        val shift = PlanPoint(
            x = graph.points.minOf { it.x } - positions.minOf { it.x },
            y = graph.points.minOf { it.y } - positions.minOf { it.y },
        )
        val moved = graph.nodeIds.withIndex().associate { (index, id) ->
            id to PlanPoint(positions[index].x + shift.x, positions[index].y + shift.y)
        }
        val rebuilt = nodes.map { node ->
            val position = moved[node.id] ?: return@map node
            node.copy(x = position.x, y = position.y)
        }
        val unchanged = nodes.indices.all { index ->
            val before = nodes[index]
            val after = rebuilt[index]
            abs(before.x - after.x) <= POSITION_TOLERANCE &&
                abs(before.y - after.y) <= POSITION_TOLERANCE
        }
        if (unchanged) return this
        return copy(nodes = rebuilt).withWorldFittingDrawing()
    }

    private fun buildGraph(document: PlanDocument): Graph? {
        val nodesById = document.nodes.associateBy { it.id }
        val drawn = mutableListOf<DrawnWall>()
        document.edges.forEach { edge ->
            val start = nodesById[edge.startNodeId] ?: return@forEach
            val end = nodesById[edge.endNodeId] ?: return@forEach
            if (hypot(end.x - start.x, end.y - start.y) <= 0.0) return@forEach
            drawn += DrawnWall(edge, start, end)
        }
        if (drawn.isEmpty()) return null

        val usedIds = drawn.flatMap { listOf(it.start.id, it.end.id) }.toSet()
        val nodeIds = document.nodes.map { it.id }.filter { it in usedIds }
        val nodeIndex = nodeIds.withIndex().associate { (index, id) -> id to index }
        val walls = drawn.map { wall ->
            val dx = wall.end.x - wall.start.x
            val dy = wall.end.y - wall.start.y
            val length = hypot(dx, dy)
            Wall(
                startIndex = nodeIndex.getValue(wall.start.id),
                endIndex = nodeIndex.getValue(wall.end.id),
                direction = PlanPoint(dx / length, dy / length),
                length = length,
            )
        }
        val wallIndexByEdgeId = drawn
            .mapIndexed { index, wall -> wall.edge.id to index }
            .toMap()

        val neighbours = List(nodeIds.size) { mutableListOf<Int>() }
        walls.forEachIndexed { index, wall ->
            neighbours[wall.startIndex] += index
            neighbours[wall.endIndex] += index
        }

        val parent = IntArray(nodeIds.size) { -1 }
        val parentWall = IntArray(nodeIds.size) { -1 }
        val parentSign = DoubleArray(nodeIds.size)
        val visited = BooleanArray(nodeIds.size)
        val walkOrder = mutableListOf<Int>()
        for (root in nodeIds.indices) {
            if (visited[root]) continue
            visited[root] = true
            walkOrder += root
            val queue = ArrayDeque<Int>()
            queue.addLast(root)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                neighbours[node].forEach { wallIndex ->
                    val wall = walls[wallIndex]
                    val other = if (wall.startIndex == node) wall.endIndex else wall.startIndex
                    if (visited[other]) return@forEach
                    visited[other] = true
                    parent[other] = node
                    parentWall[other] = wallIndex
                    parentSign[other] = if (wall.startIndex == node) 1.0 else -1.0
                    walkOrder += other
                    queue.addLast(other)
                }
            }
        }

        // Every edge outside the spanning tree closes a loop. Its two ends must meet after a walk
        // along the tree, which gives one linear requirement on the lengths per loop.
        val treeWalls = parentWall.filter { it >= 0 }.toSet()
        val terms = arrayOfNulls<List<Pair<Int, Double>>>(nodeIds.size)
        walkOrder.forEach { node ->
            terms[node] = if (parent[node] < 0) {
                emptyList()
            } else {
                terms[parent[node]]!! + (parentWall[node] to parentSign[node])
            }
        }
        val closures = walls.indices
            .filter { it !in treeWalls }
            .map { wallIndex ->
                val wall = walls[wallIndex]
                val coefficients = HashMap<Int, Double>()
                terms[wall.endIndex]!!.forEach { (index, sign) ->
                    coefficients.merge(index, sign, Double::plus)
                }
                terms[wall.startIndex]!!.forEach { (index, sign) ->
                    coefficients.merge(index, -sign, Double::plus)
                }
                coefficients.merge(wallIndex, -1.0, Double::plus)
                coefficients.filterValues { it != 0.0 }
            }

        return Graph(
            nodeIds = nodeIds,
            walls = walls,
            wallIndexByEdgeId = wallIndexByEdgeId,
            points = nodeIds.map { id -> nodesById.getValue(id).let { PlanPoint(it.x, it.y) } },
            parent = parent,
            parentWall = parentWall,
            parentSign = parentSign,
            walkOrder = walkOrder,
            closures = closures,
        )
    }

    /** One linear requirement on the lengths of the fitted drawing: `row · lengths = rhs`. */
    private class Constraint(val row: DoubleArray, val rhs: Double)

    /** A dimension waiting to be fitted, ordered by the moment it was entered. */
    private class OrderedDimension(
        val updatedAt: Long,
        val index: Int,
        val wallIndex: Int,
        val target: Double,
    )

    /** An edge as it is drawn: fixed direction, length to solve for. */
    private class Wall(
        val startIndex: Int,
        val endIndex: Int,
        val direction: PlanPoint,
        val length: Double,
    )

    private class DrawnWall(val edge: PlanEdge, val start: PlanNode, val end: PlanNode)

    /**
     * The drawing as a graph with a spanning tree.
     *
     * Positions are rebuilt by walking the tree, so every edge keeps its direction exactly: an edge
     * vector is `length * direction` by construction, never a subtraction of two coordinates.
     */
    private class Graph(
        val nodeIds: List<String>,
        val walls: List<Wall>,
        val wallIndexByEdgeId: Map<String, Int>,
        val points: List<PlanPoint>,
        private val parent: IntArray,
        private val parentWall: IntArray,
        private val parentSign: DoubleArray,
        private val walkOrder: List<Int>,
        private val closures: List<Map<Int, Double>>,
    ) {

        /** Node positions for [lengths], with every root kept where it is drawn. */
        fun positions(lengths: DoubleArray): List<PlanPoint> {
            val result = MutableList(nodeIds.size) { PlanPoint(0.0, 0.0) }
            walkOrder.forEach { node ->
                val parentNode = parent[node]
                result[node] = if (parentNode < 0) {
                    points[node]
                } else {
                    val wall = walls[parentWall[node]]
                    val from = result[parentNode]
                    val step = parentSign[node] * lengths[parentWall[node]]
                    PlanPoint(
                        x = from.x + step * wall.direction.x,
                        y = from.y + step * wall.direction.y,
                    )
                }
            }
            return result
        }

        /** One requirement per loop and axis: the walk around the loop returns to its start. */
        fun closureConstraints(): List<Constraint> {
            if (closures.isEmpty()) return emptyList()
            val size = walls.size
            return closures.flatMap { coefficients ->
                val x = DoubleArray(size)
                val y = DoubleArray(size)
                coefficients.forEach { (index, coefficient) ->
                    val direction = walls[index].direction
                    x[index] += coefficient * direction.x
                    y[index] += coefficient * direction.y
                }
                listOf(Constraint(x, 0.0), Constraint(y, 0.0))
            }
        }
    }
}
