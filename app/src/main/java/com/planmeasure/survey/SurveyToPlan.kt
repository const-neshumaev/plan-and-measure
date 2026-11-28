package com.planmeasure.survey

import com.planmeasure.plans.PlanContent
import com.planmeasure.plans.PlanContour
import com.planmeasure.plans.PlanDimension
import com.planmeasure.plans.PlanEdge
import com.planmeasure.plans.PlanNode
import com.planmeasure.plans.PlanWorld
import java.util.UUID
import kotlin.math.min

/**
 * Converts a photo sketch into the drawing of a new plan.
 *
 * The conversion carries what the user has drawn, not a measurement: the plan gets the same lines,
 * the same connections and the same dimension labels, but no scale. A photo is distorted by
 * perspective, so choosing a scale automatically would make the plan look more precise than it is;
 * the user enters a dimension in the plan when the shape is corrected (ADR 0021 and 0023).
 *
 * The sketch is fitted proportionally into the default plan sheet and centred there, with a margin.
 * Coordinates are not snapped to the plan grid: the grid belongs to the plan editor, and snapping
 * here would invent geometry the user did not draw.
 *
 * A plan dimension has no comment field, so comments of survey labels are not carried over; they
 * stay in the survey, which conversion never deletes.
 */
object SurveyToPlan {

    /** Content of a plan that repeats the sketch of [survey], or `null` when nothing is drawn. */
    fun convert(
        survey: SurveyDocument,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): PlanContent? {
        val segments = survey.segments.filter { it.start != it.end }
        if (segments.isEmpty()) return null

        val points = segments.flatMap { listOf(it.start, it.end) }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val scale = min(
            if (width > EPSILON) AVAILABLE_WIDTH / width else Double.POSITIVE_INFINITY,
            if (height > EPSILON) AVAILABLE_HEIGHT / height else Double.POSITIVE_INFINITY,
        )
        val offsetX = (PlanWorld.DEFAULT_WIDTH - width * scale) / 2 - minX * scale
        val offsetY = (PlanWorld.DEFAULT_HEIGHT - height * scale) / 2 - minY * scale

        val nodeIds = HashMap<SurveyPoint, String>()
        val nodes = mutableListOf<PlanNode>()

        fun nodeFor(point: SurveyPoint): String {
            nodeIds[point]?.let { return it }
            val id = newId()
            nodeIds[point] = id
            nodes += PlanNode(
                id = id,
                x = point.x * scale + offsetX,
                y = point.y * scale + offsetY,
            )
            return id
        }

        val chains = mutableListOf<Chain>()
        segments.forEach { segment ->
            val startNodeId = nodeFor(segment.start)
            val endNodeId = nodeFor(segment.end)
            val edge = PlanEdge(
                id = newId(),
                startNodeId = startNodeId,
                endNodeId = endNodeId,
                dimension = segment.dimension?.let { dimension ->
                    PlanDimension(
                        valueMm = dimension.valueMm,
                        source = dimension.source,
                        updatedAt = dimension.updatedAt,
                    )
                },
            )

            // Same chain rule as a drawn plan: continue an open chain whose last end is this
            // start, otherwise start a new contour. A closed chain is never extended.
            val chain = chains.indexOfLast { it.isOpen() && it.endNodeId == startNodeId }
            if (chain >= 0) {
                val target = chains[chain]
                target.edges += edge
                target.endNodeId = endNodeId
            } else {
                chains += Chain(id = newId(), edges = mutableListOf(edge), endNodeId = endNodeId)
            }
        }

        return PlanContent(
            nodes = nodes,
            contours = chains.map { PlanContour(id = it.id, edges = it.edges) },
        )
    }

    private class Chain(
        val id: String,
        val edges: MutableList<PlanEdge>,
        var endNodeId: String,
    ) {
        /** Mirrors [PlanContour.isClosed]: three edges that end where they started. */
        fun isOpen(): Boolean =
            edges.size < 3 || edges.first().startNodeId != endNodeId
    }

    private const val EPSILON = 1e-9
    private const val SHEET_MARGIN = 20.0
    private const val AVAILABLE_WIDTH = PlanWorld.DEFAULT_WIDTH - 2 * SHEET_MARGIN
    private const val AVAILABLE_HEIGHT = PlanWorld.DEFAULT_HEIGHT - 2 * SHEET_MARGIN
}
