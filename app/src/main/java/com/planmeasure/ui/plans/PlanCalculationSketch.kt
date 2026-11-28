package com.planmeasure.ui.plans

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.plans.PlanCalculation
import com.planmeasure.plans.PlanCalculationKind
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanMeasurements
import com.planmeasure.plans.PlanPoint
import kotlin.math.max
import kotlin.math.min

/**
 * Small fitted sketch of one calculation.
 *
 * The whole drawing is shown faintly and the measured part is highlighted, so the user can relate
 * the name of the calculation to the plan without leaving the details dialog. The sketch is not a
 * viewport: it always fits the drawing and does not use the editor's zoom or pan.
 */
@Composable
internal fun PlanCalculationSketch(
    plan: PlanDocument,
    calculation: PlanCalculation,
    contour: PlanMeasurements.PlanContourMeasurement?,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val selectedEdgeIds = remember(plan, calculation) {
        selectedEdgeIds(plan, calculation)
    }
    val fillPoints = remember(plan, calculation) {
        if (calculation.kind != PlanCalculationKind.AREA) {
            null
        } else if (calculation.edgeIds.isNotEmpty()) {
            PlanMeasurements.loopPoints(plan, calculation.edgeIds)
        } else {
            calculation.contourId?.let { PlanMeasurements.contourPoints(plan, it) }
        }
    }
    val holePoints = remember(plan, contour, calculation.subtractHoles) {
        if (!calculation.subtractHoles) {
            emptyList()
        } else {
            contour?.holes
                ?.filter { it.edgeIds.isNotEmpty() }
                ?.mapNotNull { PlanMeasurements.loopPoints(plan, it.edgeIds) }
                .orEmpty()
        }
    }
    val description = stringResource(R.string.plan_calculation_sketch_description)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.small)
            .background(surface)
            .semantics { contentDescription = description }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (plan.nodes.isEmpty()) {
            Text(
                text = stringResource(R.string.plan_calculation_sketch_empty),
                style = MaterialTheme.typography.bodySmall,
                color = outline,
            )
            return@Box
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val points = plan.nodes.map { PlanPoint(it.x, it.y) }
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val drawingWidth = max(maxX - minX, 1e-6)
            val drawingHeight = max(maxY - minY, 1e-6)
            val scale = min(size.width / drawingWidth.toFloat(), size.height / drawingHeight.toFloat())
            val offsetX = (size.width - drawingWidth.toFloat() * scale) / 2f - minX.toFloat() * scale
            val offsetY = (size.height - drawingHeight.toFloat() * scale) / 2f - minY.toFloat() * scale

            fun toPx(point: PlanPoint): Offset = Offset(
                x = offsetX + point.x.toFloat() * scale,
                y = offsetY + point.y.toFloat() * scale,
            )

            fillPoints?.takeIf { it.size >= 3 }?.let { pointsInLoop ->
                val path = Path()
                val first = toPx(pointsInLoop.first())
                path.moveTo(first.x, first.y)
                pointsInLoop.drop(1).forEach { point ->
                    val pixel = toPx(point)
                    path.lineTo(pixel.x, pixel.y)
                }
                path.close()
                drawPath(path, accent.copy(alpha = 0.20f))
            }

            // Cut-outs are filled with the sketch background, so the highlighted area shows what
            // the calculation really subtracts instead of a solid outer outline.
            holePoints.forEach { pointsInHole ->
                if (pointsInHole.size < 3) return@forEach
                val path = Path()
                val first = toPx(pointsInHole.first())
                path.moveTo(first.x, first.y)
                pointsInHole.drop(1).forEach { point ->
                    val pixel = toPx(point)
                    path.lineTo(pixel.x, pixel.y)
                }
                path.close()
                drawPath(path, surface)
            }

            val nodesById = plan.nodes.associateBy { it.id }
            plan.edges.distinctBy { it.id }.forEach { edge ->
                val start = nodesById[edge.startNodeId] ?: return@forEach
                val end = nodesById[edge.endNodeId] ?: return@forEach
                val selected = edge.id in selectedEdgeIds
                val startPx = toPx(PlanPoint(start.x, start.y))
                val endPx = toPx(PlanPoint(end.x, end.y))
                if (selected) {
                    drawLine(
                        color = accent.copy(alpha = 0.22f),
                        start = startPx,
                        end = endPx,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawLine(
                    color = if (selected) accent else outline.copy(alpha = 0.35f),
                    start = startPx,
                    end = endPx,
                    strokeWidth = if (selected) 2.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun selectedEdgeIds(plan: PlanDocument, calculation: PlanCalculation): Set<String> =
    if (calculation.edgeIds.isNotEmpty()) {
        calculation.edgeIds.toSet()
    } else {
        calculation.contourId
            ?.let { id -> plan.contours.firstOrNull { it.id == id }?.edges?.map { it.id }?.toSet() }
            .orEmpty()
    }
