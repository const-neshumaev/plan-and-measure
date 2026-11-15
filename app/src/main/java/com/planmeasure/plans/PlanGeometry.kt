package com.planmeasure.plans

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** A point of the drawing plane; see [PlanWorld] for its unit. */
data class PlanPoint(val x: Double, val y: Double)

/**
 * Size of the drawing world in grid units.
 *
 * The world is fixed rather than derived from the screen: a plan drawn on one phone must open
 * with the same proportions on another device and in another orientation. The canvas scales the
 * whole world uniformly to fit.
 */
object PlanWorld {
    const val WIDTH = 20.0
    const val HEIGHT = 28.0
}

/** End of a segment: the snapped point plus the existing node when the finger hit one. */
data class SegmentSnap(val point: PlanPoint, val node: PlanNode?)

/**
 * Snapping rules of the drawing.
 *
 * Free-hand movement is reduced to whole grid units and 45° directions, because the plan is a
 * sketch of straight walls, not a free-form picture. A finger near an existing node snaps to it,
 * which is how contours are joined and closed.
 */
object PlanGeometry {

    /** Shortest segment in world units; a shorter drag is treated as an accidental touch. */
    const val MIN_SEGMENT = 1.0

    private const val ANGLE_STEP = Math.PI / 4

    /**
     * How close the finger must aim to a multiple of 45° for the edge to be aligned exactly.
     *
     * A finger cannot hit an arbitrary angle, but a real room has slanted walls too, so 45° is a
     * magnet instead of a rule. The value is a code constant on purpose: it is calibrated in use and
     * must not become another setting the user has to understand.
     */
    private const val ANGLE_MAGNET = Math.PI / 18

    /** Start point of a gesture: an existing node under the finger, otherwise the grid. */
    fun snapStart(nodes: List<PlanNode>, raw: PlanPoint, magnetRadius: Double): PlanPoint {
        val node = nearestNode(nodes, raw, magnetRadius)
        return if (node != null) PlanPoint(node.x, node.y) else snapToGrid(raw)
    }

    /** Rounds a raw touch point to the nearest grid crossing inside the world. */
    fun snapToGrid(point: PlanPoint): PlanPoint = PlanPoint(
        round(point.x.coerceIn(0.0, PlanWorld.WIDTH)),
        round(point.y.coerceIn(0.0, PlanWorld.HEIGHT)),
    )

    /** Nearest node to [point] within [radius] world units, or `null`. */
    fun nearestNode(nodes: List<PlanNode>, point: PlanPoint, radius: Double): PlanNode? {
        var best: PlanNode? = null
        var bestDistance = radius
        nodes.forEach { node ->
            val distance = hypot(node.x - point.x, node.y - point.y)
            if (distance <= bestDistance) {
                best = node
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * End of a segment started at [startPoint].
     *
     * An existing node around the finger wins over the grid, so contours can be joined and
     * closed. Otherwise the end lands on a grid crossing: when the finger aims within
     * [ANGLE_MAGNET] of a multiple of 45° the edge is aligned exactly to that direction, so the
     * walls of a room come out straight without aiming; any other angle is kept as drawn, because
     * rooms have slanted walls too. Returns `null` when the result would be shorter than
     * [MIN_SEGMENT] or when the end would be the start node itself.
     */
    fun snapSegment(
        nodes: List<PlanNode>,
        startPoint: PlanPoint,
        raw: PlanPoint,
        magnetRadius: Double,
    ): SegmentSnap? {
        val magnet = nearestNode(nodes, raw, magnetRadius)
        if (magnet != null) {
            if (distance(startPoint, PlanPoint(magnet.x, magnet.y)) < MIN_SEGMENT) return null
            return SegmentSnap(PlanPoint(magnet.x, magnet.y), magnet)
        }
        val end = snapDirection(startPoint, raw) ?: return null
        return SegmentSnap(end, null)
    }

    /**
     * Target position of a dragged node, or `null` when the move is not allowed.
     *
     * The node stays on the grid even when moved by hand: a plan that mixes exact grid crossings
     * with finger coordinates of unknown precision cannot be read back reliably. A move that
     * would collapse an incident edge into a point is refused, because the edge would lose its
     * direction and could never be selected again.
     */
    fun snapNodeMove(
        nodes: List<PlanNode>,
        edges: List<PlanEdge>,
        node: PlanNode,
        raw: PlanPoint,
    ): PlanPoint? {
        val target = snapToGrid(raw)
        val collapses = edges.any { edge ->
            val otherId = when (node.id) {
                edge.startNodeId -> edge.endNodeId
                edge.endNodeId -> edge.startNodeId
                else -> null
            }
            val other = nodes.firstOrNull { it.id == otherId } ?: return@any false
            distance(PlanPoint(other.x, other.y), target) < MIN_SEGMENT
        }
        return if (collapses) null else target
    }

    /**
     * Offset of a dragged edge, or `null` when the move is not allowed.
     *
     * The edge is moved as a whole: both of its nodes get the same offset, so its direction and
     * length never change. The offset is rounded to whole grid units, because a node that leaves
     * the grid cannot be read back reliably. Neighbours keep the node at their other end, so they
     * stretch and may lose their 45° direction: that is intended, the user slides a wall instead of
     * redrawing it, and sees the result while dragging.
     *
     * [from] is the point of the edge that the finger grabbed, [to] the current finger position in
     * world units; their difference is the requested offset.
     */
    fun snapEdgeMove(
        nodes: List<PlanNode>,
        edges: List<PlanEdge>,
        edge: PlanEdge,
        from: PlanPoint,
        to: PlanPoint,
    ): PlanPoint? {
        val start = nodes.firstOrNull { it.id == edge.startNodeId } ?: return null
        val end = nodes.firstOrNull { it.id == edge.endNodeId } ?: return null
        val moved = setOf(start.id, end.id)

        val dx = clampOffset(
            raw = round(to.x - from.x),
            minimum = -min(start.x, end.x),
            maximum = PlanWorld.WIDTH - max(start.x, end.x),
        )
        val dy = clampOffset(
            raw = round(to.y - from.y),
            minimum = -min(start.y, end.y),
            maximum = PlanWorld.HEIGHT - max(start.y, end.y),
        )
        if (dx == 0.0 && dy == 0.0) return PlanPoint(0.0, 0.0)

        // An edge with both nodes moved keeps its length; an edge with only one of them moved can
        // collapse into a point, which would make it impossible to select again.
        val collapses = edges.any { candidate ->
            val startMoved = candidate.startNodeId in moved
            if (startMoved == (candidate.endNodeId in moved)) return@any false
            val startNode = nodes.firstOrNull { it.id == candidate.startNodeId } ?: return@any false
            val endNode = nodes.firstOrNull { it.id == candidate.endNodeId } ?: return@any false
            val moving = if (startMoved) startNode else endNode
            val fixed = if (startMoved) endNode else startNode
            distance(
                PlanPoint(moving.x + dx, moving.y + dy),
                PlanPoint(fixed.x, fixed.y),
            ) < MIN_SEGMENT
        }
        return if (collapses) null else PlanPoint(dx, dy)
    }

    /**
     * Point of [edge] closest to [point], used as the anchor of the grab feedback.
     *
     * A finger rarely lands exactly on a thin line, so the feedback is drawn on the line itself:
     * it then belongs to the edge the user is holding instead of floating beside it.
     */
    fun projectOnEdge(nodes: List<PlanNode>, edge: PlanEdge, point: PlanPoint): PlanPoint? {
        val start = nodes.firstOrNull { it.id == edge.startNodeId } ?: return null
        val end = nodes.firstOrNull { it.id == edge.endNodeId } ?: return null
        return projectOnSegment(point, PlanPoint(start.x, start.y), PlanPoint(end.x, end.y))
    }

    /**
     * Edge under a tap: the nearest one whose line comes within [tolerance] world units.
     *
     * Tolerance is the only hit area, because a line is thin and a finger is not; the caller
     * converts the touch radius from dp to world units.
     */
    fun nearestEdge(
        nodes: List<PlanNode>,
        contours: List<PlanContour>,
        point: PlanPoint,
        tolerance: Double,
    ): PlanEdge? {
        val nodesById = nodes.associateBy { it.id }
        var best: PlanEdge? = null
        var bestDistance = tolerance
        contours.forEach { contour ->
            contour.edges.forEach { edge ->
                val start = nodesById[edge.startNodeId] ?: return@forEach
                val end = nodesById[edge.endNodeId] ?: return@forEach
                val distance = distanceToSegment(
                    point = point,
                    start = PlanPoint(start.x, start.y),
                    end = PlanPoint(end.x, end.y),
                )
                if (distance <= bestDistance) {
                    best = edge
                    bestDistance = distance
                }
            }
        }
        return best
    }

    private fun distance(first: PlanPoint, second: PlanPoint): Double =
        hypot(second.x - first.x, second.y - first.y)

    private fun distanceToSegment(point: PlanPoint, start: PlanPoint, end: PlanPoint): Double =
        distance(point, projectOnSegment(point, start, end))

    private fun projectOnSegment(point: PlanPoint, start: PlanPoint, end: PlanPoint): PlanPoint {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return start
        val projection = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
        val t = projection.coerceIn(0.0, 1.0)
        return PlanPoint(start.x + t * dx, start.y + t * dy)
    }

    /**
     * Keeps an offset inside [minimum]..[maximum]; when the range is empty the edge cannot move at
     * all, which can only happen with a corrupt document whose node is outside the world.
     */
    private fun clampOffset(raw: Double, minimum: Double, maximum: Double): Double =
        if (minimum > maximum) 0.0 else raw.coerceIn(minimum, maximum)

    private fun snapDirection(start: PlanPoint, raw: PlanPoint): PlanPoint? {
        val dx = raw.x - start.x
        val dy = raw.y - start.y
        if (hypot(dx, dy) < MIN_SEGMENT) return null

        val angle = atan2(dy, dx)
        val aligned = round(angle / ANGLE_STEP) * ANGLE_STEP
        if (abs(angle - aligned) <= ANGLE_MAGNET) {
            return snapAlongDirection(start, dx, dy, aligned)
        }

        val end = snapToGrid(raw)
        return if (distance(start, end) < MIN_SEGMENT) null else end
    }

    /** End of a segment pulled onto the exact [angle] by the magnet, or `null` when not allowed. */
    private fun snapAlongDirection(
        start: PlanPoint,
        dx: Double,
        dy: Double,
        angle: Double,
    ): PlanPoint? {
        val dirX = round(cos(angle)).toInt()
        val dirY = round(sin(angle)).toInt()
        if (dirX == 0 && dirY == 0) return null

        // The direction is a grid step, not a unit vector: a diagonal step is √2 long, so the
        // projection must be divided by the squared length of the direction. Without it a 45° line
        // came out twice as long as the distance the finger travelled.
        val directionLengthSquared = (dirX * dirX + dirY * dirY).toDouble()
        val steps = (dx * dirX + dy * dirY) / directionLengthSquared
        val maxLength = maxLengthInsideWorld(start, dirX, dirY)
        if (maxLength < MIN_SEGMENT) return null

        val length = min(round(steps), floor(maxLength))
        if (length < MIN_SEGMENT) return null
        return PlanPoint(start.x + dirX * length, start.y + dirY * length)
    }

    /** How far a segment can travel from [start] along ([dirX], [dirY]) before leaving the world. */
    private fun maxLengthInsideWorld(start: PlanPoint, dirX: Int, dirY: Int): Double =
        min(
            axisLimit(start.x, dirX, PlanWorld.WIDTH),
            axisLimit(start.y, dirY, PlanWorld.HEIGHT),
        )

    private fun axisLimit(value: Double, direction: Int, max: Double): Double = when {
        direction > 0 -> max - value
        direction < 0 -> value
        else -> Double.MAX_VALUE
    }
}
