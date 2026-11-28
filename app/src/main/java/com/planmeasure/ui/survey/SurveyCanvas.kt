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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.planmeasure.ui.plans.PlanViewport
import com.planmeasure.ui.plans.SheetMargin
import com.planmeasure.ui.plans.placeDimensionLabels
import com.planmeasure.ui.plans.planTransform
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

/** Drawing surface of one survey: the photo, the sketched lines and their dimension labels. */
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
                    gestureStart = null
                    previewEnd = null
                    editPreview = null
                    trackViewGesture(event, liveViewport.value, canvas, world, padding) {
                        liveOnViewportChange.value(it)
                    }
                }
            } finally {
                val end = previewEnd
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
            color = borderColor,
            topLeft = origin,
            size = contentSize,
            style = Stroke(width = 1.dp.toPx()),
        )

        rendered.segments.forEach { segment ->
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
                    color = lineColor.copy(alpha = 0.25f),
                    start = startPx,
                    end = endPx,
                    strokeWidth = width * 2.4f,
                    cap = StrokeCap.Round,
                )
            }
            // A light halo keeps the line visible over both dark and light photos.
            drawLine(
                color = haloColor.copy(alpha = 0.75f),
                start = startPx,
                end = endPx,
                strokeWidth = width + 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = startPx,
                end = endPx,
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
            listOf(startPx, endPx).forEach { center ->
                drawCircle(color = nodeFill, radius = NodeRadius.toPx(), center = center)
                drawCircle(
                    color = lineColor,
                    radius = NodeRadius.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        val start = gestureStart
        val end = previewEnd
        if (start != null && end != null && start != end) {
            drawLine(
                color = lineColor,
                start = transform.toPx(start.toPlanPoint()),
                end = transform.toPx(end.toPlanPoint()),
                strokeWidth = LineWidth.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                ),
            )
        }

        if (labelLayouts.isNotEmpty()) {
            val segmentsById = rendered.segments.associateBy { it.id }
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
                    color = labelFill,
                    topLeft = Offset(label.topLeft.x - paddingX, label.topLeft.y - paddingY),
                    size = Size(layout.size.width + 2 * paddingX, height),
                    cornerRadius = CornerRadius(height / 2f),
                )
                drawText(
                    textLayoutResult = layout,
                    color = labelContent,
                    topLeft = label.topLeft,
                )
            }
        }
    }
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
