package com.planmeasure.plans

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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
     * closed; otherwise the direction is rounded to a multiple of 45° and the length to whole
     * units. Returns `null` when the result would be shorter than [MIN_SEGMENT] or when the end
     * would be the start node itself.
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

    private fun distance(first: PlanPoint, second: PlanPoint): Double =
        hypot(second.x - first.x, second.y - first.y)

    private fun snapDirection(start: PlanPoint, raw: PlanPoint): PlanPoint? {
        val dx = raw.x - start.x
        val dy = raw.y - start.y
        if (hypot(dx, dy) < MIN_SEGMENT) return null

        val angle = round(atan2(dy, dx) / ANGLE_STEP) * ANGLE_STEP
        val dirX = round(cos(angle)).toInt()
        val dirY = round(sin(angle)).toInt()
        if (dirX == 0 && dirY == 0) return null

        val projected = dx * dirX + dy * dirY
        val maxLength = maxLengthInsideWorld(start, dirX, dirY)
        if (maxLength < MIN_SEGMENT) return null

        val length = min(round(projected), floor(maxLength))
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
