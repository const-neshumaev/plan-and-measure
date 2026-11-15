package com.planmeasure.plans

import kotlinx.serialization.Serializable

/** Current version of the on-disk plan document. Bump when the shape changes. */
const val PLAN_SCHEMA_VERSION = 1

/**
 * A point of the drawing in abstract world units, not pixels: see [PlanWorld] and ADR 0004.
 */
@Serializable
data class PlanNode(
    val id: String,
    val x: Double,
    val y: Double,
)

/**
 * One straight side of a contour.
 *
 * The edge is a separate entity with its own id because a measured distance will be attached to
 * it later; coordinates alone cannot identify the side the user picked.
 */
@Serializable
data class PlanEdge(
    val id: String,
    val startNodeId: String,
    val endNodeId: String,
)

/**
 * A chain of connected edges, for example a room outline.
 *
 * Closure is derived from geometry instead of being stored: a contour is closed when its last
 * edge returns to the starting node. A separate flag could drift away from the edges it describes.
 */
@Serializable
data class PlanContour(
    val id: String,
    val edges: List<PlanEdge> = emptyList(),
) {
    val isClosed: Boolean
        get() = edges.size >= 3 && edges.first().startNodeId == edges.last().endNodeId
}

/** One named drawing, stored as `plans/<id>.json`. */
@Serializable
data class PlanDocument(
    val schemaVersion: Int = PLAN_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val nodes: List<PlanNode> = emptyList(),
    val contours: List<PlanContour> = emptyList(),
) {
    val edgeCount: Int
        get() = contours.sumOf { it.edges.size }

    val hasDrawing: Boolean
        get() = edgeCount > 0
}
