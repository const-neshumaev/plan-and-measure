package com.planmeasure.plans

import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.units.LengthUnit
import kotlinx.serialization.Serializable

/** Current version of the on-disk plan document. Bump when the shape changes. */
const val PLAN_SCHEMA_VERSION = 7

/**
 * Size of the drawing sheet in grid units; the sheet is a rectangle starting at the origin.
 *
 * The size is stored with the drawing instead of being a constant: the sheet grows when a wall is
 * pulled past its border, and a plan opened on another device must keep the sheet it was drawn on.
 * Documents written before version 2 get the default size, see [PlanWorld].
 */
@Serializable
data class PlanSize(
    val width: Double = PlanWorld.DEFAULT_WIDTH,
    val height: Double = PlanWorld.DEFAULT_HEIGHT,
)

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
 * The edge is a separate entity with its own id because a measured distance can be attached to it;
 * coordinates alone cannot identify the side the user picked.
 */
@Serializable
data class PlanEdge(
    val id: String,
    val startNodeId: String,
    val endNodeId: String,
    val dimension: PlanDimension? = null,
)

/**
 * A distance the user attached to one edge, see ADR 0007, 0009 and 0012.
 *
 * The value is canonical millimeters (see `com.planmeasure.units.Length`). The unit of the plan
 * decides how the same value is shown, so changing it never rewrites the dimension.
 *
 * The dimension is what gives the plan its scale: once one exists, the drawing is reshaped to hold
 * it, and a value the fixed angles cannot accommodate stays a label. See [PlanScale].
 */
@Serializable
data class PlanDimension(
    val valueMm: String,
    val source: MeasurementSource,
    val updatedAt: Long,
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

/** What a saved plan calculation measures. */
@Serializable
enum class PlanCalculationKind { AREA, LENGTH }

/**
 * A named measurement the user asked the plan to show, see ADR 0020.
 *
 * The calculation stores what to measure, not the number: the value is derived from the current
 * geometry every time it is shown. Reshaping the plan or changing a dimension then updates the
 * result instead of leaving a stale number behind.
 *
 * An area calculation holds the edges of exactly one simple closed loop. The loop is derived from
 * the graph, so a rectangle drawn as two chains from one corner to another is as measurable as one
 * drawn in a single stroke. Nested loops inside it are subtracted unless [subtractHoles] is off.
 * A length calculation holds any set of edges, which is how the user sums several walls without
 * drawing them as one contour. [contourId] is kept for area calculations written before loops were
 * derived from edges. [comment] is the user's own note about what was measured.
 */
@Serializable
data class PlanCalculation(
    val id: String,
    val name: String,
    val kind: PlanCalculationKind,
    /** Legacy area target; new area calculations store [edgeIds] instead. */
    val contourId: String? = null,
    val edgeIds: List<String> = emptyList(),
    val subtractHoles: Boolean = true,
    val createdAt: Long,
    /** Free-form note shown with the calculation; empty when the user did not add one. */
    val comment: String = "",
)

/** One named drawing, stored as `plans/<id>.json`. */
@Serializable
data class PlanDocument(
    val schemaVersion: Int = PLAN_SCHEMA_VERSION,
    val id: String,
    val name: String,
    /** Unit of every dimension of this plan; the drawing itself stays in abstract grid units. */
    val unit: LengthUnit = LengthUnit.METERS,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val world: PlanSize = PlanSize(),
    /**
     * Millimeters per grid unit, or `null` while the plan is an unscaled sketch.
     *
     * It is a ratio, not a length, and it belongs to the plan because the drawing itself is in
     * abstract grid units: the first dimension sets the ratio, and every later dimension is
     * converted with it (ADR 0012). The ratio is stored instead of being derived from the oldest
     * dimension, otherwise deleting one wall would rescale the whole drawing.
     */
    val mmPerUnit: Double? = null,
    /** Edge whose dimension set [mmPerUnit]; re-measuring it moves the scale too. */
    val scaleEdgeId: String? = null,
    val nodes: List<PlanNode> = emptyList(),
    val contours: List<PlanContour> = emptyList(),
    /** Saved measurements of this drawing; their values are derived, not stored (ADR 0020). */
    val calculations: List<PlanCalculation> = emptyList(),
) {
    val edgeCount: Int
        get() = contours.sumOf { it.edges.size }

    /** All edges of all contours; a node can be shared by edges of different contours. */
    val edges: List<PlanEdge>
        get() = contours.flatMap { it.edges }

    val hasDrawing: Boolean
        get() = edgeCount > 0
}

/**
 * Drawing content for a new plan, for example one converted from a survey.
 *
 * The content has no scale on purpose: a converted sketch keeps its dimension labels but stays an
 * unscaled drawing until the user enters a dimension in the plan itself (ADR 0023).
 */
data class PlanContent(
    val nodes: List<PlanNode> = emptyList(),
    val contours: List<PlanContour> = emptyList(),
)
