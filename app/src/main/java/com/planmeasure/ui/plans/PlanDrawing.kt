package com.planmeasure.ui.plans

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanEdge
import com.planmeasure.plans.PlanGeometry
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanScale
import com.planmeasure.plans.PlanSize
import com.planmeasure.ui.components.labelRes
import com.planmeasure.units.Length
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Free space around the fitted sheet, deliberately generous: the finger has to be able to pull a
 * node past the border of a sheet that fills the screen, otherwise the sheet can never grow.
 */
internal val SheetMargin = 32.dp

/** Margins of the small preview in the list; the thumbnail has no room for [SheetMargin]. */
private val ThumbnailPadding = 4.dp

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
private const val FIT_REFIT_MILLIS = 250

/** Prefix of a dimension the drawing does not match; the symbol is the same in every language. */
private const val MISMATCH_MARK = "≈ "

/** Below this spacing the grid lines merge into a solid field and the grid is not drawn. */
private val MinGridSpacing = 8.dp

/**
 * Steps of the grid, from one unit to the coordinate limit.
 *
 * The drawn step is the finest one whose lines stay readable, and every [MAJOR_GRID_STEP]-th line
 * of that step is emphasized. Neighbouring steps differ by two or two and a half times, so the
 * density looks the same at any zoom instead of switching abruptly or turning into a solid field.
 */
private val GridSteps = listOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000)

/**
 * What a drag on the canvas does.
 *
 * [Draw] adds walls, [Edit] reshapes the finished plan, and [Dimensions] only attaches or changes
 * a distance on a wall: a separate tool keeps the three gestures from competing for one touch.
 */
enum class PlanTool { Draw, Edit, Dimensions }

/**
 * Drawing surface of one plan.
 *
 * A drag becomes one straight edge: the start snaps to a node or the grid, the direction to 45°
 * and the end to the grid. The callback receives raw world points and the magnet radius, because
 * the same rules must be applied by [com.planmeasure.plans.PlanEditing]. A tap without a drag
 * inserts a point on the wall under the finger, which splits the wall into two edges.
 *
 * In [PlanTool.Edit] the finger can take three things:
 * - a node, grabbed on the first down and moved with the grid snap;
 * - an edge, grabbed after a long press and slid as a whole, so its direction does not change;
 * - empty space, where a tap only clears the selection.
 *
 * A tap on an edge selects it; a second tap on the already selected edge inserts a point there.
 *
 * In [PlanTool.Dimensions] a tap only selects a wall, because the same tap opens the dimension
 * card; nothing is dragged, and two fingers still move the view.
 *
 * Two fingers always mean the view: a pinch zooms around the fingers, a two-finger drag moves the
 * sheet. A second finger arriving in the middle of a single-finger gesture drops that gesture
 * without writing anything, so looking at the plan never edits it.
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
    onMoveNode: (nodeId: String, to: PlanPoint, magnetRadius: Double) -> Unit,
    onMoveEdge: (edgeId: String, from: PlanPoint, to: PlanPoint) -> Unit,
    onSelectEdge: (edgeId: String?) -> Unit,
    onSplitEdge: (edgeId: String, at: PlanPoint) -> Unit,
    modifier: Modifier = Modifier,
    selectedEdgeId: String? = null,
    showGrid: Boolean = true,
    viewport: PlanViewport = PlanViewport(),
    onViewportChange: (PlanViewport) -> Unit = {},
) {
    val edgeColor = MaterialTheme.colorScheme.onSurface
    val nodeFill = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val labelFill = MaterialTheme.colorScheme.primaryContainer
    val labelContent = MaterialTheme.colorScheme.onPrimaryContainer
    val mismatchFill = MaterialTheme.colorScheme.errorContainer
    val mismatchContent = MaterialTheme.colorScheme.onErrorContainer
    val haptic = LocalHapticFeedback.current

    // Dimension text is laid out in the composition, not on every frame: measuring texts during
    // a pinch would run on each pointer move.
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val unitLabel = stringResource(plan.unit.labelRes())
    // A dimension the fixed angles cannot hold stays a label, and the drawing disagrees with it
    // (ADR 0012). The mark keeps the value readable while showing that the wall is drawn differently.
    val mismatched = remember(plan) { PlanScale.mismatchedEdgeIds(plan) }
    val labelLayouts = plan.edges.mapNotNull { edge ->
        val dimension = edge.dimension ?: return@mapNotNull null
        val mark = if (edge.id in mismatched) MISMATCH_MARK else ""
        val text = "$mark${Length.format(dimension.valueMm, plan.unit)} $unitLabel"
        edge.id to textMeasurer.measure(AnnotatedString(text), labelStyle)
    }.toMap()

    var gestureStart by remember { mutableStateOf<PlanPoint?>(null) }
    var previewEnd by remember { mutableStateOf<PlanPoint?>(null) }
    var draggedNode by remember { mutableStateOf<NodeDrag?>(null) }
    var draggedEdge by remember { mutableStateOf<EdgeDrag?>(null) }
    var rippleAnchor by remember { mutableStateOf<GrabRipple?>(null) }

    // A grown sheet refits the view. Interpolating the sheet instead of the transform keeps nodes,
    // grid and border consistent, and the drawing slides into the new sheet instead of jumping.
    val fitWidth = remember(plan.id) { Animatable(plan.world.width.toFloat()) }
    val fitHeight = remember(plan.id) { Animatable(plan.world.height.toFloat()) }
    LaunchedEffect(plan.world.width) {
        fitWidth.animateTo(plan.world.width.toFloat(), tween(FIT_REFIT_MILLIS))
    }
    LaunchedEffect(plan.world.height) {
        fitHeight.animateTo(plan.world.height.toFloat(), tween(FIT_REFIT_MILLIS))
    }

    // The gesture handler is installed once per tool and plan, so it must read the current view and
    // callback through state; captured values would stay at the composition that created it.
    val liveViewport = rememberUpdatedState(viewport)
    val liveOnViewportChange = rememberUpdatedState(onViewportChange)
    // The selection changes during the gesture (a long press selects the edge it grabbed), so the
    // handler must read the current value instead of the one captured at installation.
    val liveSelectedEdgeId = rememberUpdatedState(selectedEdgeId)

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
                val canvas = Size(size.width.toFloat(), size.height.toFloat())
                val world = PlanSize(fitWidth.value.toDouble(), fitHeight.value.toDouble())
                val padding = SheetMargin.toPx()
                val transform = planTransform(canvas, world, liveViewport.value, padding)
                val magnetRadius = (MagnetRadius.toPx() / transform.scale).toDouble()
                val tappedEdge = PlanGeometry.nearestEdge(
                    nodes = plan.nodes,
                    contours = plan.contours,
                    point = transform.toWorld(down.position),
                    tolerance = (EdgeTapRadius.toPx() / transform.scale).toDouble(),
                )
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
                var pinch: PointerEvent? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            pinch = event
                            break
                        }
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
                    pinch?.let { event ->
                        // The second finger turns the gesture into a view gesture: the unfinished
                        // segment is dropped, because the user is looking rather than drawing.
                        gestureStart = null
                        previewEnd = null
                        trackViewGesture(event, liveViewport.value, canvas, world, padding) {
                            liveOnViewportChange.value(it)
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
                    } else if (finished && !dragging && tappedEdge != null) {
                        PlanGeometry.projectOnEdge(
                            nodes = plan.nodes,
                            edge = tappedEdge,
                            point = transform.toWorld(down.position),
                        )?.let { onSplitEdge(tappedEdge.id, it) }
                    }
                }
            }
        }

        // The selection is deliberately not a key: it changes in the middle of the long press, and
        // a restarted gesture detector would drop the finger that is already on the canvas.
        PlanTool.Edit -> Modifier.pointerInput(tool, plan) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val canvas = Size(size.width.toFloat(), size.height.toFloat())
                val world = PlanSize(fitWidth.value.toDouble(), fitHeight.value.toDouble())
                val padding = SheetMargin.toPx()
                val transform = planTransform(canvas, world, liveViewport.value, padding)
                val downWorld = transform.toWorld(down.position)
                val magnetRadius = (MagnetRadius.toPx() / transform.scale).toDouble()
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
                var nodeWorld: PlanPoint? = null
                var edgeTarget: PlanPoint? = null
                var pinch: PointerEvent? = null

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
                        if (event.changes.count { it.pressed } > 1) {
                            pinch = event
                            break
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
                        val worldPoint = transform.toWorld(change.position)
                        if (grabbedNode != null) {
                            nodeWorld = worldPoint
                            nodeDrop = PlanGeometry.snapNodeMove(
                                nodes = plan.nodes,
                                edges = plan.edges,
                                node = grabbedNode,
                                raw = worldPoint,
                                magnetRadius = magnetRadius,
                            )
                            draggedNode = nodeDrop?.let { NodeDrag(grabbedNode.id, it) }
                        } else if (edgeGrabbed && candidate != null) {
                            val offset = PlanGeometry.snapEdgeMove(
                                nodes = plan.nodes,
                                edges = plan.edges,
                                world = plan.world,
                                edge = candidate.edge,
                                from = candidate.grabPoint,
                                to = worldPoint,
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
                    pinch?.let { event ->
                        // A second finger cancels the grab: the preview disappears, nothing is
                        // written, and the gesture becomes a view gesture.
                        rippleAnchor = null
                        draggedNode = null
                        draggedEdge = null
                        trackViewGesture(event, liveViewport.value, canvas, world, padding) {
                            liveOnViewportChange.value(it)
                        }
                    }
                } finally {
                    draggedNode = null
                    draggedEdge = null
                    rippleAnchor = null
                    if (finished) {
                        when {
                            grabbedNode != null && nodeDrop != null && nodeWorld != null ->
                                onMoveNode(grabbedNode.id, nodeWorld, magnetRadius)
                            // The first tap selects the edge under it, a tap on the already
                            // selected edge inserts a point, and a tap on empty space clears the
                            // selection.
                            candidate != null && !edgeGrabbed && !dragging -> {
                                if (candidate.edge.id == liveSelectedEdgeId.value) {
                                    PlanGeometry.projectOnEdge(
                                        nodes = plan.nodes,
                                        edge = candidate.edge,
                                        point = downWorld,
                                    )?.let { onSplitEdge(candidate.edge.id, it) }
                                } else {
                                    onSelectEdge(candidate.edge.id)
                                }
                            }
                            candidate == null && grabbedNode == null && !dragging ->
                                onSelectEdge(null)
                            candidate != null && edgeTarget != null ->
                                onMoveEdge(candidate.edge.id, candidate.grabPoint, edgeTarget)
                        }
                    }
                }
            }
        }

        // The selection is not a key for the same reason as in Edit: opening the dimension card
        // changes it during the gesture.
        PlanTool.Dimensions -> Modifier.pointerInput(tool, plan) {
            awaitEachGesture {
                // Touches on the dimension card are consumed by the card itself; the canvas must
                // not select a wall behind it.
                val down = awaitFirstDown(requireUnconsumed = true)
                val canvas = Size(size.width.toFloat(), size.height.toFloat())
                val world = PlanSize(fitWidth.value.toDouble(), fitHeight.value.toDouble())
                val padding = SheetMargin.toPx()
                val transform = planTransform(canvas, world, liveViewport.value, padding)
                val candidate = PlanGeometry.nearestEdge(
                    nodes = plan.nodes,
                    contours = plan.contours,
                    point = transform.toWorld(down.position),
                    tolerance = (EdgeTapRadius.toPx() / transform.scale).toDouble(),
                )

                var dragged = false
                var tapped = false
                var pinch: PointerEvent? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            pinch = event
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            tapped = !dragged
                            break
                        }
                        if ((change.position - down.position).getDistance() >
                            viewConfiguration.touchSlop
                        ) {
                            dragged = true
                        }
                    }
                    pinch?.let { event ->
                        trackViewGesture(event, liveViewport.value, canvas, world, padding) {
                            liveOnViewportChange.value(it)
                        }
                    }
                } finally {
                    // A tap on empty space clears the selection, which closes the card.
                    if (tapped) onSelectEdge(candidate?.id)
                }
            }
        }
    }

    Canvas(modifier = modifier.clipToBounds().then(gesture)) {
        val world = PlanSize(fitWidth.value.toDouble(), fitHeight.value.toDouble())
        val transform = planTransform(size, world, viewport, SheetMargin.toPx())
        drawRect(
            color = gridColor.copy(alpha = 0.5f),
            topLeft = transform.topLeft(),
            size = transform.size(world),
            style = Stroke(width = 1.dp.toPx()),
        )
        if (showGrid) drawPlanGrid(transform, world, gridColor)

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
                val measured = edge.dimension != null
                val differs = edge.id in mismatched
                // A measured wall is thicker and takes the accent colour, so it separates from a
                // plain wall at a glance; the selection is a glow on top of the same colour. A wall
                // whose dimension the drawing cannot hold takes the error colour instead: the label
                // of a short wall may be hidden, and the disagreement has to stay visible anyway.
                val width = when {
                    selected -> LineWidth.toPx() * 2.4f
                    measured -> LineWidth.toPx() * 1.8f
                    else -> LineWidth.toPx()
                }
                val startPx = transform.toPx(from)
                val endPx = transform.toPx(to)
                val highlighted = if (differs) errorColor else accentColor
                if (selected) {
                    drawLine(
                        color = highlighted.copy(alpha = 0.20f),
                        start = startPx,
                        end = endPx,
                        strokeWidth = LineWidth.toPx() * 5.5f,
                        cap = StrokeCap.Round,
                    )
                }
                drawLine(
                    color = when {
                        differs -> errorColor
                        selected || measured -> accentColor
                        else -> edgeColor
                    },
                    start = startPx,
                    end = endPx,
                    strokeWidth = width,
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

        if (labelLayouts.isNotEmpty()) {
            val edgesById = plan.edges.associateBy { it.id }
            val candidates = labelLayouts.mapNotNull { (edgeId, layout) ->
                val edge = edgesById[edgeId] ?: return@mapNotNull null
                val from = pointOf(edge.startNodeId) ?: return@mapNotNull null
                val to = pointOf(edge.endNodeId) ?: return@mapNotNull null
                DimensionLabelCandidate(
                    edgeId = edgeId,
                    start = transform.toPx(from),
                    end = transform.toPx(to),
                    size = Size(layout.size.width.toFloat(), layout.size.height.toFloat()),
                )
            }
            val paddingX = 5.dp.toPx()
            val paddingY = 2.dp.toPx()
            placeDimensionLabels(
                candidates = candidates,
                selectedEdgeId = selectedEdgeId,
                gap = 6.dp.toPx(),
                spacing = 4.dp.toPx(),
            ).forEach { label ->
                val layout = labelLayouts[label.edgeId] ?: return@forEach
                val height = layout.size.height + 2 * paddingY
                val differs = label.edgeId in mismatched
                drawRoundRect(
                    color = if (differs) mismatchFill else labelFill,
                    topLeft = Offset(label.topLeft.x - paddingX, label.topLeft.y - paddingY),
                    size = Size(layout.size.width + 2 * paddingX, height),
                    cornerRadius = CornerRadius(height / 2f),
                )
                drawText(
                    textLayoutResult = layout,
                    color = if (differs) mismatchContent else labelContent,
                    topLeft = label.topLeft,
                )
            }
        }
    }
}

/**
 * Drives a two-finger view gesture until every finger is up.
 *
 * [firstEvent] is the event that brought the second finger, so the first zoom step is not lost.
 * The view is reported on every change, which keeps the canvas under the fingers while pinching.
 */
private suspend fun AwaitPointerEventScope.trackViewGesture(
    firstEvent: PointerEvent,
    start: PlanViewport,
    canvas: Size,
    world: PlanSize,
    padding: Float,
    onChange: (PlanViewport) -> Unit,
) {
    var event = firstEvent
    var viewport = start
    while (true) {
        if (event.changes.none { it.pressed }) return
        viewport = viewport.transformedBy(
            canvas = canvas,
            world = world,
            padding = padding,
            zoomChange = event.calculateZoom(),
            panChange = event.calculatePan(),
            focus = event.calculateCentroid(),
        )
        onChange(viewport)
        event = awaitPointerEvent()
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
        val transform = planTransform(size, plan.world, PlanViewport(), ThumbnailPadding.toPx())
        drawRect(
            color = borderColor,
            topLeft = transform.topLeft(),
            size = transform.size(plan.world),
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

/**
 * Grid of the sheet, limited to the visible part: a zoomed-in view must not draw thousands of lines
 * outside the canvas. Minor lines are dropped when they would merge into a solid field.
 */
private fun DrawScope.drawPlanGrid(transform: WorldTransform, world: PlanSize, color: Color) {
    val step = gridStepFor(transform.scale, MinGridSpacing.toPx()) ?: return
    val topLeft = transform.topLeft()
    val sheet = transform.size(world)
    val scale = transform.scale
    val strokeWidth = 1.dp.toPx()

    val firstX = max(0, floor((0f - topLeft.x) / scale).toInt())
    val lastX = min(world.width.toInt(), ceil((size.width - topLeft.x) / scale).toInt())
    for (x in alignedRange(firstX, lastX, step)) {
        val px = topLeft.x + x * scale
        drawLine(
            color = color.copy(alpha = gridLineAlpha(x, step)),
            start = Offset(px, topLeft.y),
            end = Offset(px, topLeft.y + sheet.height),
            strokeWidth = strokeWidth,
        )
    }

    val firstY = max(0, floor((0f - topLeft.y) / scale).toInt())
    val lastY = min(world.height.toInt(), ceil((size.height - topLeft.y) / scale).toInt())
    for (y in alignedRange(firstY, lastY, step)) {
        val py = topLeft.y + y * scale
        drawLine(
            color = color.copy(alpha = gridLineAlpha(y, step)),
            start = Offset(topLeft.x, py),
            end = Offset(topLeft.x + sheet.width, py),
            strokeWidth = strokeWidth,
        )
    }
}

/** Grid step that keeps the lines readable at [scale] pixels per unit, or `null` when none does. */
internal fun gridStepFor(scale: Float, minimumSpacing: Float): Int? {
    if (!scale.isFinite() || scale <= 0f) return null
    return GridSteps.firstOrNull { it * scale >= minimumSpacing }
}

/** Alpha of the grid line at [index]; every [MAJOR_GRID_STEP]-th line of [step] is emphasized. */
internal fun gridLineAlpha(index: Int, step: Int): Float =
    if (index % (step * MAJOR_GRID_STEP) == 0) 0.5f else 0.22f

/** Indices of [first]..[last] that are multiples of [step]. */
private fun alignedRange(first: Int, last: Int, step: Int): IntProgression =
    (first - first % step)..last step step
