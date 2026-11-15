package com.planmeasure.ui.plans

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanGeometry
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanWorld
import kotlin.math.min

private val CanvasPadding = 10.dp
private val MagnetRadius = 18.dp
private val NodeGrabRadius = 24.dp
private val EdgeTapRadius = 16.dp
private val NodeRadius = 5.dp
private val LineWidth = 2.5.dp
private const val MAJOR_GRID_STEP = 5

/** What a drag on the canvas does: draw a new wall or reshape the existing plan. */
enum class PlanTool { Draw, Edit }

/**
 * Maps plan world units to canvas pixels.
 *
 * The whole world is fitted uniformly and centered, so the drawing keeps its proportions on any
 * screen and in any orientation. [padding] keeps nodes on the world border away from the edges.
 */
internal class WorldTransform(width: Float, height: Float, padding: Float = 0f) {

    val scale: Float = min(
        (width - 2 * padding) / PlanWorld.WIDTH.toFloat(),
        (height - 2 * padding) / PlanWorld.HEIGHT.toFloat(),
    ).coerceAtLeast(0.001f)

    private val offsetX = (width - PlanWorld.WIDTH.toFloat() * scale) / 2f
    private val offsetY = (height - PlanWorld.HEIGHT.toFloat() * scale) / 2f

    fun toPx(point: PlanPoint): Offset = Offset(
        x = offsetX + point.x.toFloat() * scale,
        y = offsetY + point.y.toFloat() * scale,
    )

    fun toWorld(offset: Offset): PlanPoint = PlanPoint(
        x = ((offset.x - offsetX) / scale).toDouble(),
        y = ((offset.y - offsetY) / scale).toDouble(),
    )

    fun worldTopLeft(): Offset = Offset(offsetX, offsetY)

    fun worldSize(): Size = Size(
        width = PlanWorld.WIDTH.toFloat() * scale,
        height = PlanWorld.HEIGHT.toFloat() * scale,
    )
}

/**
 * Drawing surface of one plan.
 *
 * A drag becomes one straight edge: the start snaps to a node or the grid, the direction to 45°
 * and the end to the grid. The callback receives raw world points and the magnet radius, because
 * the same rules must be applied by [com.planmeasure.plans.PlanEditing].
 *
 * In [PlanTool.Edit] the same drag moves the node under the finger, snapped to the grid, and a tap
 * selects or clears an edge. The preview is local state: nothing is written until the gesture ends.
 */
@Composable
fun PlanCanvas(
    plan: PlanDocument,
    tool: PlanTool,
    onSegment: (from: PlanPoint, to: PlanPoint, magnetRadius: Double) -> Unit,
    onMoveNode: (nodeId: String, to: PlanPoint) -> Unit,
    onSelectEdge: (edgeId: String?) -> Unit,
    modifier: Modifier = Modifier,
    selectedEdgeId: String? = null,
    showGrid: Boolean = true,
) {
    val edgeColor = MaterialTheme.colorScheme.onSurface
    val nodeFill = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val accentColor = MaterialTheme.colorScheme.primary

    var gestureStart by remember { mutableStateOf<PlanPoint?>(null) }
    var previewEnd by remember { mutableStateOf<PlanPoint?>(null) }
    var draggedNode by remember { mutableStateOf<NodeDrag?>(null) }

    val gesture = when (tool) {
        PlanTool.Draw -> Modifier.pointerInput(plan) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val transform = WorldTransform(size.width.toFloat(), size.height.toFloat(), CanvasPadding.toPx())
                val magnetRadius = (MagnetRadius.toPx() / transform.scale).toDouble()
                val start = PlanGeometry.snapStart(
                    nodes = plan.nodes,
                    raw = transform.toWorld(down.position),
                    magnetRadius = magnetRadius,
                )
                gestureStart = start
                previewEnd = start

                var dragging = false
                var finished = false
                var lastPosition = down.position
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastPosition = change.position
                        if (change.changedToUpIgnoreConsumed()) {
                            finished = true
                            break
                        }
                        val moved = (change.position - down.position).getDistance()
                        if (!dragging && moved > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            previewEnd = PlanGeometry.snapSegment(
                                nodes = plan.nodes,
                                startPoint = start,
                                raw = transform.toWorld(change.position),
                                magnetRadius = magnetRadius,
                            )?.point
                        }
                    }
                } finally {
                    val end = previewEnd
                    gestureStart = null
                    previewEnd = null
                    if (finished && dragging && end != null) {
                        onSegment(
                            transform.toWorld(down.position),
                            transform.toWorld(lastPosition),
                            magnetRadius,
                        )
                    }
                }
            }
        }

        PlanTool.Edit -> Modifier.pointerInput(plan, selectedEdgeId) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val transform = WorldTransform(size.width.toFloat(), size.height.toFloat(), CanvasPadding.toPx())
                val downWorld = transform.toWorld(down.position)
                val grabbed = PlanGeometry.nearestNode(
                    nodes = plan.nodes,
                    point = downWorld,
                    radius = (NodeGrabRadius.toPx() / transform.scale).toDouble(),
                )
                val target = if (grabbed == null) {
                    PlanGeometry.nearestEdge(
                        nodes = plan.nodes,
                        contours = plan.contours,
                        point = downWorld,
                        tolerance = (EdgeTapRadius.toPx() / transform.scale).toDouble(),
                    )
                } else {
                    null
                }

                var dragging = false
                var finished = false
                var drop: PlanPoint? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            finished = true
                            break
                        }
                        val moved = (change.position - down.position).getDistance()
                        if (!dragging && moved > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            if (grabbed != null) {
                                drop = PlanGeometry.snapNodeMove(
                                    nodes = plan.nodes,
                                    edges = plan.edges,
                                    node = grabbed,
                                    raw = transform.toWorld(change.position),
                                )
                                draggedNode = drop?.let { NodeDrag(grabbed.id, it) }
                            }
                        }
                    }
                } finally {
                    draggedNode = null
                    if (finished) {
                        when {
                            // A drag on empty space does nothing; a tap selects the edge under it.
                            grabbed == null && !dragging -> onSelectEdge(target?.id)
                            grabbed != null && drop != null -> onMoveNode(grabbed.id, drop)
                        }
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.then(gesture)) {
        val transform = WorldTransform(size.width, size.height, CanvasPadding.toPx())
        if (showGrid) drawPlanGrid(transform, gridColor)

        val nodesById = plan.nodes.associateBy { it.id }
        val drag = draggedNode

        // The dragged node is drawn at its preview position, so the whole shape follows the finger.
        fun pointOf(nodeId: String): PlanPoint? = when {
            drag != null && drag.nodeId == nodeId -> drag.point
            else -> nodesById[nodeId]?.let { PlanPoint(it.x, it.y) }
        }

        plan.contours.forEach { contour ->
            contour.edges.forEach { edge ->
                val from = pointOf(edge.startNodeId) ?: return@forEach
                val to = pointOf(edge.endNodeId) ?: return@forEach
                val selected = edge.id == selectedEdgeId
                drawLine(
                    color = if (selected) accentColor else edgeColor,
                    start = transform.toPx(from),
                    end = transform.toPx(to),
                    strokeWidth = if (selected) LineWidth.toPx() * 2f else LineWidth.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        val start = gestureStart
        val end = previewEnd
        if (start != null && end != null && start != end) {
            drawLine(
                color = accentColor,
                start = transform.toPx(start),
                end = transform.toPx(end),
                strokeWidth = LineWidth.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                ),
            )
        }

        plan.nodes.forEach { node ->
            val point = pointOf(node.id) ?: return@forEach
            val center = transform.toPx(point)
            val highlighted = drag != null && drag.nodeId == node.id
            if (highlighted) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.18f),
                    radius = NodeRadius.toPx() * 2.8f,
                    center = center,
                )
            }
            drawCircle(color = nodeFill, radius = NodeRadius.toPx(), center = center)
            drawCircle(
                color = if (highlighted) accentColor else edgeColor,
                radius = NodeRadius.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private data class NodeDrag(val nodeId: String, val point: PlanPoint)

/** Small drawing preview for the saved-plans list; no grid and no nodes. */
@Composable
fun PlanThumbnail(plan: PlanDocument, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surface
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(background),
    ) {
        val transform = WorldTransform(size.width, size.height, CanvasPadding.toPx())
        drawRect(
            color = borderColor,
            topLeft = transform.worldTopLeft(),
            size = transform.worldSize(),
            style = Stroke(width = 1.dp.toPx()),
        )

        val nodesById = plan.nodes.associateBy { it.id }
        plan.contours.forEach { contour ->
            contour.edges.forEach { edge ->
                val from = nodesById[edge.startNodeId] ?: return@forEach
                val to = nodesById[edge.endNodeId] ?: return@forEach
                drawLine(
                    color = lineColor,
                    start = transform.toPx(PlanPoint(from.x, from.y)),
                    end = transform.toPx(PlanPoint(to.x, to.y)),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawPlanGrid(transform: WorldTransform, color: Color) {
    val topLeft = transform.worldTopLeft()
    val world = transform.worldSize()

    for (x in 0..PlanWorld.WIDTH.toInt()) {
        val px = topLeft.x + x * transform.scale
        drawLine(
            color = color.copy(alpha = gridAlpha(x)),
            start = Offset(px, topLeft.y),
            end = Offset(px, topLeft.y + world.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
    for (y in 0..PlanWorld.HEIGHT.toInt()) {
        val py = topLeft.y + y * transform.scale
        drawLine(
            color = color.copy(alpha = gridAlpha(y)),
            start = Offset(topLeft.x, py),
            end = Offset(topLeft.x + world.width, py),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun gridAlpha(index: Int): Float =
    if (index % MAJOR_GRID_STEP == 0) 0.5f else 0.22f
