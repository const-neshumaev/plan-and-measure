package com.planmeasure.ui.plans

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanEdge
import com.planmeasure.plans.PlanGeometry
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanWorld
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull

private val CanvasPadding = 10.dp
private val MagnetRadius = 18.dp
private val NodeGrabRadius = 24.dp
private val EdgeTapRadius = 16.dp
private val NodeRadius = 5.dp
private val LineWidth = 2.5.dp
private val RippleMaxRadius = 88.dp
private val RippleStrokeWidth = 2.dp
private const val RIPPLE_RINGS = 3
private const val RIPPLE_PERIOD_MILLIS = 1100
private const val LONG_PRESS_GRAB_MILLIS = 300L
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
 * In [PlanTool.Edit] the finger can take three things:
 * - a node, grabbed on the first down and moved with the grid snap;
 * - an edge, grabbed after a long press and slid as a whole, so its direction does not change;
 * - empty space, where a tap only clears the selection.
 *
 * Every grab is answered with expanding rings and a haptic tick: the finger covers the node or the
 * line, so without them the user cannot tell whether the grab happened. The preview is local state:
 * nothing is written until the gesture ends.
 */
@Composable
fun PlanCanvas(
    plan: PlanDocument,
    tool: PlanTool,
    onSegment: (from: PlanPoint, to: PlanPoint, magnetRadius: Double) -> Unit,
    onMoveNode: (nodeId: String, to: PlanPoint) -> Unit,
    onMoveEdge: (edgeId: String, from: PlanPoint, to: PlanPoint) -> Unit,
    onSelectEdge: (edgeId: String?) -> Unit,
    modifier: Modifier = Modifier,
    selectedEdgeId: String? = null,
    showGrid: Boolean = true,
) {
    val edgeColor = MaterialTheme.colorScheme.onSurface
    val nodeFill = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    var gestureStart by remember { mutableStateOf<PlanPoint?>(null) }
    var previewEnd by remember { mutableStateOf<PlanPoint?>(null) }
    var draggedNode by remember { mutableStateOf<NodeDrag?>(null) }
    var draggedEdge by remember { mutableStateOf<EdgeDrag?>(null) }
    var rippleAnchor by remember { mutableStateOf<GrabRipple?>(null) }

    // The rings repeat while the grab lasts: a single burst is easy to miss when the finger starts
    // moving at once.
    val rippleProgress = remember { Animatable(0f) }
    LaunchedEffect(rippleAnchor) {
        if (rippleAnchor == null) return@LaunchedEffect
        rippleProgress.snapTo(0f)
        while (true) {
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = RIPPLE_PERIOD_MILLIS,
                    easing = LinearEasing,
                ),
            )
            rippleProgress.snapTo(0f)
        }
    }

    // The tool is a key of both branches on purpose: a pointerInput element is considered equal
    // when its keys match, and its handler is then not updated, so two branches with the same keys
    // would keep the handler that was installed first (the tool would stop switching).
    val gesture = when (tool) {
        PlanTool.Draw -> Modifier.pointerInput(tool, plan) {
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

        // The selection is deliberately not a key: it changes in the middle of the long press, and
        // a restarted gesture detector would drop the finger that is already on the canvas.
        PlanTool.Edit -> Modifier.pointerInput(tool, plan) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val transform = WorldTransform(size.width.toFloat(), size.height.toFloat(), CanvasPadding.toPx())
                val downWorld = transform.toWorld(down.position)
                val grabbedNode = PlanGeometry.nearestNode(
                    nodes = plan.nodes,
                    point = downWorld,
                    radius = (NodeGrabRadius.toPx() / transform.scale).toDouble(),
                )
                val candidate = if (grabbedNode != null) {
                    null
                } else {
                    PlanGeometry.nearestEdge(
                        nodes = plan.nodes,
                        contours = plan.contours,
                        point = downWorld,
                        tolerance = (EdgeTapRadius.toPx() / transform.scale).toDouble(),
                    )?.let { edge ->
                        EdgeCandidate(
                            edge = edge,
                            grabPoint = PlanGeometry.projectOnEdge(plan.nodes, edge, downWorld) ?: downWorld,
                        )
                    }
                }

                var dragging = false
                var finished = false
                var edgeGrabbed = false
                var nodeDrop: PlanPoint? = null
                var edgeTarget: PlanPoint? = null

                if (grabbedNode != null) {
                    // The node is taken on the first down, so the feedback must not wait for
                    // movement: the user has to learn about the hit before pulling the finger.
                    rippleAnchor = GrabRipple.Node(grabbedNode.id)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                try {
                    var waitingForLongPress = candidate != null
                    val longPressDeadline = down.uptimeMillis + LONG_PRESS_GRAB_MILLIS
                    var lastEventTime = down.uptimeMillis
                    while (true) {
                        val event = if (waitingForLongPress) {
                            withTimeoutOrNull((longPressDeadline - lastEventTime).coerceAtLeast(1L)) {
                                awaitPointerEvent()
                            }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // The finger stayed down and still for the whole timeout: the edge is
                            // taken for moving. The selection is set here so the line is already
                            // highlighted while the user drags it.
                            waitingForLongPress = false
                            edgeGrabbed = true
                            candidate?.let {
                                rippleAnchor = GrabRipple.Edge(it.edge.id, it.grabPoint)
                                onSelectEdge(it.edge.id)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastEventTime = change.uptimeMillis
                        if (change.changedToUpIgnoreConsumed()) {
                            finished = true
                            break
                        }
                        val moved = (change.position - down.position).getDistance()
                        if (waitingForLongPress && moved > viewConfiguration.touchSlop) {
                            // A quick drag of a line has no meaning: moving an edge is the long
                            // press gesture, so the movement cancels the wait.
                            waitingForLongPress = false
                        }
                        if (!dragging && moved > viewConfiguration.touchSlop) dragging = true
                        if (!dragging) continue
                        change.consume()
                        val world = transform.toWorld(change.position)
                        if (grabbedNode != null) {
                            nodeDrop = PlanGeometry.snapNodeMove(
                                nodes = plan.nodes,
                                edges = plan.edges,
                                node = grabbedNode,
                                raw = world,
                            )
                            draggedNode = nodeDrop?.let { NodeDrag(grabbedNode.id, it) }
                        } else if (edgeGrabbed && candidate != null) {
                            val offset = PlanGeometry.snapEdgeMove(
                                nodes = plan.nodes,
                                edges = plan.edges,
                                edge = candidate.edge,
                                from = candidate.grabPoint,
                                to = world,
                            )
                            // A refused position keeps the last allowed one: throwing the edge back
                            // to its start would look like a lost grab.
                            if (offset != null) {
                                edgeTarget = PlanPoint(
                                    x = candidate.grabPoint.x + offset.x,
                                    y = candidate.grabPoint.y + offset.y,
                                )
                                draggedEdge = EdgeDrag(candidate.edge.id, offset)
                            }
                        }
                    }
                } finally {
                    draggedNode = null
                    draggedEdge = null
                    rippleAnchor = null
                    if (finished) {
                        when {
                            grabbedNode != null && nodeDrop != null -> onMoveNode(grabbedNode.id, nodeDrop)
                            // A tap selects the edge under it; a tap on empty space clears it.
                            candidate != null && !edgeGrabbed && !dragging ->
                                onSelectEdge(candidate.edge.id)
                            candidate == null && grabbedNode == null && !dragging ->
                                onSelectEdge(null)
                            candidate != null && edgeTarget != null ->
                                onMoveEdge(candidate.edge.id, candidate.grabPoint, edgeTarget)
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
        val nodeDrag = draggedNode
        val edgeDrag = draggedEdge

        // Nodes moved by the current gesture are drawn at their preview position, so the whole
        // shape follows the finger. An edge drag shifts exactly its two nodes; the neighbours stay
        // in place, and the user sees them stretch before the change is written.
        val overrides: Map<String, PlanPoint> = when {
            nodeDrag != null -> mapOf(nodeDrag.nodeId to nodeDrag.point)
            edgeDrag != null -> {
                val edge = plan.edges.firstOrNull { it.id == edgeDrag.edgeId }
                edge?.let {
                    listOf(it.startNodeId, it.endNodeId).mapNotNull { nodeId ->
                        nodesById[nodeId]?.let { node ->
                            nodeId to PlanPoint(
                                x = node.x + edgeDrag.offset.x,
                                y = node.y + edgeDrag.offset.y,
                            )
                        }
                    }.toMap()
                }.orEmpty()
            }
            else -> emptyMap()
        }

        fun pointOf(nodeId: String): PlanPoint? =
            overrides[nodeId] ?: nodesById[nodeId]?.let { PlanPoint(it.x, it.y) }

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
            val highlighted = nodeDrag != null && nodeDrag.nodeId == node.id
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

        val ripple = rippleAnchor
        if (ripple != null) {
            val center = when (ripple) {
                is GrabRipple.Node -> pointOf(ripple.nodeId)?.let { transform.toPx(it) }
                is GrabRipple.Edge -> {
                    // The edge moves with the preview offset, so its rings travel with it.
                    val offset = edgeDrag?.takeIf { it.edgeId == ripple.edgeId }?.offset
                        ?: PlanPoint(0.0, 0.0)
                    transform.toPx(
                        PlanPoint(
                            x = ripple.grabPoint.x + offset.x,
                            y = ripple.grabPoint.y + offset.y,
                        ),
                    )
                }
            }
            if (center != null) drawGrabRipple(center, rippleProgress.value, accentColor)
        }
    }
}

private data class NodeDrag(val nodeId: String, val point: PlanPoint)

private data class EdgeDrag(val edgeId: String, val offset: PlanPoint)

/** Edge under the finger and the point of it that the press grabbed; both are needed to slide it. */
private data class EdgeCandidate(val edge: PlanEdge, val grabPoint: PlanPoint)

/** What the grab feedback is attached to while the finger holds it. */
private sealed interface GrabRipple {
    data class Node(val nodeId: String) : GrabRipple
    data class Edge(val edgeId: String, val grabPoint: PlanPoint) : GrabRipple
}

/**
 * Rings spreading from the grabbed point and fading out.
 *
 * They grow past a fingertip on purpose: a ring hidden under the finger says nothing. The rings
 * repeat while the grab lasts, see the animation in [PlanCanvas].
 */
private fun DrawScope.drawGrabRipple(center: Offset, progress: Float, color: Color) {
    repeat(RIPPLE_RINGS) { index ->
        val phase = (progress + index.toFloat() / RIPPLE_RINGS) % 1f
        drawCircle(
            color = color.copy(alpha = (1f - phase) * (1f - phase) * 0.55f),
            radius = RippleMaxRadius.toPx() * phase,
            center = center,
            style = Stroke(width = RippleStrokeWidth.toPx()),
        )
    }
}

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
