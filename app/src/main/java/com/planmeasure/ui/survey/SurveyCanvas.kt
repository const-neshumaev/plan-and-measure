package com.planmeasure.ui.survey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanSize
import com.planmeasure.survey.SurveyDocument
import com.planmeasure.survey.SurveyEditing
import com.planmeasure.survey.SurveyEndpoint
import com.planmeasure.survey.SurveyGeometry
import com.planmeasure.survey.SurveyPoint
import com.planmeasure.ui.components.labelRes
import com.planmeasure.ui.plans.DimensionLabelCandidate
import com.planmeasure.ui.plans.MagnifierDiameter
import com.planmeasure.ui.plans.MagnifierGap
import com.planmeasure.ui.plans.MagnifierZoom
import com.planmeasure.ui.plans.PlanViewport
import com.planmeasure.ui.plans.SheetMargin
import com.planmeasure.ui.plans.WorldTransform
import com.planmeasure.ui.plans.drawMagnifier
import com.planmeasure.ui.plans.magnifierCenter
import com.planmeasure.ui.plans.placeDimensionLabels
import com.planmeasure.ui.plans.planTransform
import com.planmeasure.ui.plans.rememberMagnifierState
import com.planmeasure.ui.plans.transformedBy
import com.planmeasure.units.Length
import kotlin.math.roundToInt

/** What a single finger does in the survey editor. */
enum class SurveyTool { Draw, Edit, Dimensions }

private val SegmentTapRadius = 18.dp
private val EndpointSnapRadius = 20.dp
private val EndpointGrabRadius = 24.dp
private val LineWidth = 2.5.dp
private val NodeRadius = 3.5.dp

/**
 * Drawing surface of one survey: the photo, the sketched lines and their dimension labels.
 *
 * In [SurveyTool.Draw] and [SurveyTool.Edit] a loupe repeats the scene around the finger at
 * [MagnifierZoom], so the endpoint it covers stays visible while it is joined. The loupe has no
 * pointer input and the gesture logic is unchanged.
 */
@Composable
fun SurveyCanvas(
    survey: SurveyDocument,
    photo: ImageBitmap,
    tool: SurveyTool,
    selectedSegmentId: String?,
    viewport: PlanViewport,
    onViewportChange: (PlanViewport) -> Unit,
    onAddSegment: (from: SurveyPoint, to: SurveyPoint, snapRadius: Double) -> Unit,
    onMoveEndpoint: (
        segmentId: String,
        endpoint: SurveyEndpoint,
        to: SurveyPoint,
        snapRadius: Double,
    ) -> Unit,
    onMoveSegment: (segmentId: String, delta: SurveyPoint) -> Unit,
    onSelectSegment: (String?) -> Unit,
    modifier: Modifier = Modifier,
    showMagnifier: Boolean = true,
) {
    val sheet = remember(survey.photo) { SurveyGeometry.sheetFor(survey.photo) }
    val world = remember(sheet) { PlanSize(sheet.width, sheet.height) }
    val padding = with(LocalDensity.current) { SheetMargin.toPx() }

    val lineColor = MaterialTheme.colorScheme.primary
    val nodeFill = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val labelFill = MaterialTheme.colorScheme.primaryContainer
    val labelContent = MaterialTheme.colorScheme.onPrimaryContainer
    val haloColor = MaterialTheme.colorScheme.surface
    val haptic = LocalHapticFeedback.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val magnifier = rememberMagnifierState()
    val paint = SurveyPaint(
        lineColor = lineColor,
        nodeFill = nodeFill,
        borderColor = borderColor,
        labelFill = labelFill,
        labelContent = labelContent,
        haloColor = haloColor,
    )

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val unitLabel = stringResource(survey.unit.labelRes())

    var gestureStart by remember { mutableStateOf<SurveyPoint?>(null) }
    var previewEnd by remember { mutableStateOf<SurveyPoint?>(null) }
    // A drag in Edit is previewed by a document copy, so the saved document changes only once, on
    // release, and one drag stays one undo step.
    var editPreview by remember { mutableStateOf<SurveyDocument?>(null) }
    val rendered = editPreview ?: survey
    val labelLayouts = remember(rendered.segments, survey.unit, unitLabel, labelStyle) {
        rendered.segments.mapNotNull { segment ->
            val dimension = segment.dimension ?: return@mapNotNull null
            val text = "${Length.format(dimension.valueMm, survey.unit)} $unitLabel"
            segment.id to textMeasurer.measure(AnnotatedString(text), labelStyle)
        }.toMap()
    }

    // Gesture handlers are installed per tool and document, so they read the current view and
    // callbacks through state instead of values captured at installation.
    val liveViewport = rememberUpdatedState(viewport)
    val liveOnViewportChange = rememberUpdatedState(onViewportChange)
    val liveOnAddSegment = rememberUpdatedState(onAddSegment)
    val liveOnMoveEndpoint = rememberUpdatedState(onMoveEndpoint)
    val liveOnMoveSegment = rememberUpdatedState(onMoveSegment)
    val liveOnSelectSegment = rememberUpdatedState(onSelectSegment)

    val gesture = Modifier.pointerInput(tool, survey) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (tool != SurveyTool.Dimensions) magnifier.touch = down.position
            val canvas = Size(size.width.toFloat(), size.height.toFloat())
            val transform = planTransform(canvas, world, liveViewport.value, padding)
            val downWorld = toSurveyPoint(transform.toWorld(down.position))
            val snapRadius = (EndpointSnapRadius.toPx() / transform.scale).toDouble()
            val grabRadius = (EndpointGrabRadius.toPx() / transform.scale).toDouble()
            val tapTolerance = (SegmentTapRadius.toPx() / transform.scale).toDouble()
            val start = SurveyGeometry.clamp(
                SurveyGeometry.snapPoint(survey.segments, downWorld, snapRadius),
                sheet,
            )
            val endpointHit = if (tool == SurveyTool.Edit) {
                SurveyGeometry.nearestSegmentEndpoint(survey.segments, downWorld, grabRadius)
            } else {
                null
            }
            val segmentHit = if (tool == SurveyTool.Edit && endpointHit == null) {
                SurveyGeometry.nearestSegment(survey.segments, downWorld, tapTolerance)
            } else {
                null
            }
            when {
                tool == SurveyTool.Edit && (endpointHit != null || segmentHit != null) ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                tool == SurveyTool.Draw && start != downWorld ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            var dragging = false
            var finished = false
            var lastWorld = downWorld
            var pinch: PointerEvent? = null
            if (tool == SurveyTool.Draw) {
                gestureStart = start
                previewEnd = start
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) {
                        pinch = event
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    lastWorld = toSurveyPoint(transform.toWorld(change.position))
                    if (tool != SurveyTool.Dimensions) magnifier.touch = change.position
                    if (change.changedToUpIgnoreConsumed()) {
                        finished = true
                        break
                    }
                    val moved = (change.position - down.position).getDistance()
                    if (!dragging && moved > viewConfiguration.touchSlop) dragging = true
                    if (!dragging) continue
                    change.consume()
                    when (tool) {
                        SurveyTool.Draw -> {
                            previewEnd = SurveyGeometry.clamp(
                                SurveyGeometry.snapPoint(survey.segments, lastWorld, snapRadius),
                                sheet,
                            )
                        }

                        SurveyTool.Edit -> {
                            editPreview = when {
                                endpointHit != null -> SurveyEditing.moveEndpoint(
                                    document = survey,
                                    segmentId = endpointHit.segmentId,
                                    endpoint = endpointHit.endpoint,
                                    to = lastWorld,
                                    snapRadius = snapRadius,
                                )

                                segmentHit != null -> SurveyEditing.moveSegment(
                                    document = survey,
                                    segmentId = segmentHit.id,
                                    delta = SurveyPoint(
                                        lastWorld.x - downWorld.x,
                                        lastWorld.y - downWorld.y,
                                    ),
                                )

                                else -> null
                            }
                        }

                        SurveyTool.Dimensions -> Unit
                    }
                }
                pinch?.let { event ->
                    // A second finger means viewing: the unfinished change is dropped, because the
                    // user is looking at the photo rather than editing it.
                    magnifier.touch = null
                    gestureStart = null
                    previewEnd = null
                    editPreview = null
                    trackViewGesture(event, liveViewport.value, canvas, world, padding) {
                        liveOnViewportChange.value(it)
                    }
                }
            } finally {
                val end = previewEnd
                magnifier.touch = null
                gestureStart = null
                previewEnd = null
                editPreview = null
                if (finished) {
                    when {
                        tool == SurveyTool.Draw && dragging && end != null ->
                            liveOnAddSegment.value(start, end, snapRadius)

                        tool == SurveyTool.Edit && dragging && endpointHit != null ->
                            liveOnMoveEndpoint.value(
                                endpointHit.segmentId,
                                endpointHit.endpoint,
                                lastWorld,
                                snapRadius,
                            )

                        tool == SurveyTool.Edit && dragging && segmentHit != null ->
                            liveOnMoveSegment.value(
                                segmentHit.id,
                                SurveyPoint(
                                    lastWorld.x - downWorld.x,
                                    lastWorld.y - downWorld.y,
                                ),
                            )

                        tool == SurveyTool.Edit && !dragging ->
                            liveOnSelectSegment.value(endpointHit?.segmentId ?: segmentHit?.id)

                        tool == SurveyTool.Dimensions && !dragging -> {
                            val segment = SurveyGeometry.nearestSegment(
                                segments = survey.segments,
                                point = downWorld,
                                tolerance = tapTolerance,
                            )
                            if (segment != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            liveOnSelectSegment.value(segment?.id)
                        }
                    }
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
            .then(gesture),
    ) {
        val transform = planTransform(size, world, viewport, padding)
        drawSurveyScene(
            survey = rendered,
            photo = photo,
            world = world,
            transform = transform,
            paint = paint,
            selectedSegmentId = selectedSegmentId,
            labelLayouts = labelLayouts,
            previewStart = gestureStart,
            previewEnd = previewEnd,
            showLabels = true,
        )

        // The loupe repeats the photo and the sketch around the finger, so the endpoint it covers
        // stays visible while the user joins it. Gestures never reach it.
        val loupeAlpha = magnifier.alpha.value
        if (showMagnifier && loupeAlpha > 0.01f) {
            val radius = MagnifierDiameter.toPx() / 2f
            val anchor = magnifier.anchor
            val center = magnifierCenter(
                anchor = anchor,
                canvas = size,
                radius = radius,
                gap = MagnifierGap.toPx(),
            )
            val snapTarget = surveySnapTarget(survey, rendered, gestureStart, previewEnd)
            drawMagnifier(
                anchor = anchor,
                center = center,
                radius = radius,
                alpha = loupeAlpha,
                zoom = MagnifierZoom,
                fillColor = surfaceColor,
                rimColor = lineColor,
                shadowColor = Color.Black,
                crosshairColor = lineColor,
            ) {
                drawSurveyScene(
                    survey = rendered,
                    photo = photo,
                    world = world,
                    transform = transform,
                    paint = paint,
                    selectedSegmentId = selectedSegmentId,
                    labelLayouts = emptyMap(),
                    previewStart = gestureStart,
                    previewEnd = previewEnd,
                    showLabels = false,
                )
                snapTarget?.let { drawSurveySnapTarget(it, transform, lineColor) }
            }
        }
    }
}

/** Colours of one survey scene, shared by the canvas and its loupe. */
private class SurveyPaint(
    val lineColor: Color,
    val nodeFill: Color,
    val borderColor: Color,
    val labelFill: Color,
    val labelContent: Color,
    val haloColor: Color,
)

/** Draws the photo, the sketched lines and, optionally, their dimension labels. */
private fun DrawScope.drawSurveyScene(
    survey: SurveyDocument,
    photo: ImageBitmap,
    world: PlanSize,
    transform: WorldTransform,
    paint: SurveyPaint,
    selectedSegmentId: String?,
    labelLayouts: Map<String, TextLayoutResult>,
    previewStart: SurveyPoint?,
    previewEnd: SurveyPoint?,
    showLabels: Boolean,
) {
    val origin = transform.topLeft()
    val contentSize = transform.size(world)

    drawImage(
        image = photo,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(photo.width, photo.height),
        dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
        dstSize = IntSize(
            width = contentSize.width.roundToInt().coerceAtLeast(1),
            height = contentSize.height.roundToInt().coerceAtLeast(1),
        ),
        filterQuality = FilterQuality.Medium,
    )
    drawRect(
        color = paint.borderColor,
        topLeft = origin,
        size = contentSize,
        style = Stroke(width = 1.dp.toPx()),
    )

    survey.segments.forEach { segment ->
        val selected = segment.id == selectedSegmentId
        val measured = segment.dimension != null
        val startPx = transform.toPx(segment.start.toPlanPoint())
        val endPx = transform.toPx(segment.end.toPlanPoint())
        val width = when {
            selected -> LineWidth.toPx() * 1.8f
            measured -> LineWidth.toPx() * 1.5f
            else -> LineWidth.toPx()
        }
        if (selected) {
            drawLine(
                color = paint.lineColor.copy(alpha = 0.25f),
                start = startPx,
                end = endPx,
                strokeWidth = width * 2.4f,
                cap = StrokeCap.Round,
            )
        }
        // A light halo keeps the line visible over both dark and light photos.
        drawLine(
            color = paint.haloColor.copy(alpha = 0.75f),
            start = startPx,
            end = endPx,
            strokeWidth = width + 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = paint.lineColor,
            start = startPx,
            end = endPx,
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
        listOf(startPx, endPx).forEach { center ->
            drawCircle(color = paint.nodeFill, radius = NodeRadius.toPx(), center = center)
            drawCircle(
                color = paint.lineColor,
                radius = NodeRadius.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }

    if (previewStart != null && previewEnd != null && previewStart != previewEnd) {
        drawLine(
            color = paint.lineColor,
            start = transform.toPx(previewStart.toPlanPoint()),
            end = transform.toPx(previewEnd.toPlanPoint()),
            strokeWidth = LineWidth.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
            ),
        )
    }

    if (showLabels && labelLayouts.isNotEmpty()) {
        val segmentsById = survey.segments.associateBy { it.id }
        val candidates = labelLayouts.mapNotNull { (segmentId, layout) ->
            val segment = segmentsById[segmentId] ?: return@mapNotNull null
            DimensionLabelCandidate(
                edgeId = segmentId,
                start = transform.toPx(segment.start.toPlanPoint()),
                end = transform.toPx(segment.end.toPlanPoint()),
                size = Size(layout.size.width.toFloat(), layout.size.height.toFloat()),
            )
        }
        val paddingX = 5.dp.toPx()
        val paddingY = 2.dp.toPx()
        placeDimensionLabels(
            candidates = candidates,
            selectedEdgeId = selectedSegmentId,
            gap = 6.dp.toPx(),
            spacing = 4.dp.toPx(),
        ).forEach { label ->
            val layout = labelLayouts[label.edgeId] ?: return@forEach
            val height = layout.size.height + 2 * paddingY
            drawRoundRect(
                color = paint.labelFill,
                topLeft = Offset(label.topLeft.x - paddingX, label.topLeft.y - paddingY),
                size = Size(layout.size.width + 2 * paddingX, height),
                cornerRadius = CornerRadius(height / 2f),
            )
            drawText(
                textLayoutResult = layout,
                color = paint.labelContent,
                topLeft = label.topLeft,
            )
        }
    }
}

/** Existing endpoint the current preview landed on, if the snap caught one. */
private fun surveySnapTarget(
    original: SurveyDocument,
    rendered: SurveyDocument,
    previewStart: SurveyPoint?,
    previewEnd: SurveyPoint?,
): SurveyPoint? {
    val target = when {
        previewStart != null && previewEnd != null -> previewEnd
        else -> movedEndpoint(original, rendered)
    } ?: return null
    return original.segments
        .flatMap { listOf(it.start, it.end) }
        .firstOrNull { it.x == target.x && it.y == target.y }
}

/**
 * Endpoint moved by the current edit drag, or `null` when a whole line is dragged.
 *
 * A moved line changes both ends; a moved endpoint changes exactly one. The neighbours of a moved
 * line also change one end each, so the whole-line case must win over them.
 */
private fun movedEndpoint(original: SurveyDocument, rendered: SurveyDocument): SurveyPoint? {
    if (rendered === original) return null
    var wholeSegmentMoved = false
    var moved: SurveyPoint? = null
    original.segments.forEach { before ->
        val after = rendered.segments.firstOrNull { it.id == before.id } ?: return@forEach
        val startMoved = after.start != before.start
        val endMoved = after.end != before.end
        when {
            startMoved && endMoved -> wholeSegmentMoved = true
            startMoved || endMoved -> if (moved == null) {
                moved = if (startMoved) after.start else after.end
            }
        }
    }
    return if (wholeSegmentMoved) null else moved
}

/** Ring around the endpoint the snap caught, so the join is visible before the finger lifts. */
private fun DrawScope.drawSurveySnapTarget(
    point: SurveyPoint,
    transform: WorldTransform,
    color: Color,
) {
    val center = transform.toPx(point.toPlanPoint())
    drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = NodeRadius.toPx() * 3.2f,
        center = center,
    )
    drawCircle(
        color = color,
        radius = NodeRadius.toPx() * 1.9f,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
}

/** Screen position of the middle of [segmentId], used to place its dimension card. */
internal fun surveyCardAnchor(
    canvasSize: IntSize,
    survey: SurveyDocument,
    segmentId: String,
    viewport: PlanViewport,
    padding: Float,
): Offset? {
    if (canvasSize.width == 0 || canvasSize.height == 0) return null
    val segment = survey.segments.firstOrNull { it.id == segmentId } ?: return null
    val sheet = SurveyGeometry.sheetFor(survey.photo)
    val transform = planTransform(
        canvas = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        world = PlanSize(sheet.width, sheet.height),
        viewport = viewport,
        padding = padding,
    )
    val start = transform.toPx(segment.start.toPlanPoint())
    val end = transform.toPx(segment.end.toPlanPoint())
    return Offset(
        x = (start.x + end.x) / 2f,
        y = (start.y + end.y) / 2f,
    )
}

private fun SurveyPoint.toPlanPoint(): PlanPoint = PlanPoint(x, y)

private fun toSurveyPoint(point: PlanPoint): SurveyPoint = SurveyPoint(point.x, point.y)

/**
 * Drives a two-finger view gesture until every finger is up.
 *
 * [firstEvent] is the event that brought the second finger, so the first zoom step is not lost.
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
