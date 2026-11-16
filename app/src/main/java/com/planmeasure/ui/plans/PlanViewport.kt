package com.planmeasure.ui.plans

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanSize
import kotlin.math.abs
import kotlin.math.min

/**
 * Lowest zoom relative to the fitted sheet. Going below 1 leaves free room around the border, so a
 * finger can pull a wall past it on a phone instead of hitting the screen edge.
 */
private const val MIN_ZOOM = 0.5f

/** Highest zoom relative to the fitted sheet; the value is calibrated in use. */
private const val MAX_ZOOM = 8f

private const val FIT_TOLERANCE = 0.001f

/**
 * View of a plan: zoom relative to the fitted sheet and pan of the viewport in pixels.
 *
 * The sheet is part of the document, the view is not: the view lives in the editor and starts
 * fitted when a plan is opened, so the saved drawing never depends on the screen it was last seen
 * on. See ADR 0006.
 */
data class PlanViewport(
    val zoom: Float = 1f,
    val pan: Offset = Offset.Zero,
) {
    /** `true` when the whole sheet is visible and nothing is zoomed or panned. */
    val isFitted: Boolean
        get() = abs(zoom - 1f) <= FIT_TOLERANCE && pan == Offset.Zero
}

/** Keeps the view across rotation; a view is not part of the saved plan. */
internal val PlanViewportSaver: Saver<PlanViewport, Any> = listSaver(
    save = { listOf(it.zoom, it.pan.x, it.pan.y) },
    restore = { PlanViewport(zoom = it[0], pan = Offset(it[1], it[2])) },
)

/** Maps plan grid units to canvas pixels. */
internal class WorldTransform(val scale: Float, private val origin: Offset) {

    fun toPx(point: PlanPoint): Offset = Offset(
        x = origin.x + point.x.toFloat() * scale,
        y = origin.y + point.y.toFloat() * scale,
    )

    fun toWorld(offset: Offset): PlanPoint = PlanPoint(
        x = ((offset.x - origin.x) / scale).toDouble(),
        y = ((offset.y - origin.y) / scale).toDouble(),
    )

    fun topLeft(): Offset = origin

    fun size(world: PlanSize): Size = Size(
        width = world.width.toFloat() * scale,
        height = world.height.toFloat() * scale,
    )
}

/** Scale at which the whole sheet fits [canvas] with [padding] around it. */
internal fun fitScale(canvas: Size, world: PlanSize, padding: Float): Float = min(
    (canvas.width - 2 * padding) / world.width.toFloat(),
    (canvas.height - 2 * padding) / world.height.toFloat(),
).coerceAtLeast(0.001f)

/** Pixel transform of [viewport] over [world]; the pan is clamped so the sheet stays reachable. */
internal fun planTransform(
    canvas: Size,
    world: PlanSize,
    viewport: PlanViewport,
    padding: Float,
): WorldTransform {
    val scale = fitScale(canvas, world, padding) * viewport.zoom
    val fitted = centeredOrigin(canvas, world.scaled(scale))
    val pan = viewport.pan.clamped(canvas = canvas, content = world.scaled(scale))
    return WorldTransform(scale = scale, origin = fitted + pan)
}

/**
 * View after a pinch or a two-finger drag.
 *
 * The sheet point under [focus] stays under it, so the drawing zooms around the fingers instead of
 * the centre; [panChange] then follows the fingers. Zoom is limited to [MIN_ZOOM] on one end, so
 * free room around the border is always reachable, and [MAX_ZOOM] on the other; the pan is clamped
 * so the sheet cannot be thrown off screen.
 */
internal fun PlanViewport.transformedBy(
    canvas: Size,
    world: PlanSize,
    padding: Float,
    zoomChange: Float,
    panChange: Offset,
    focus: Offset,
): PlanViewport {
    if (!zoomChange.isFinite() || zoomChange <= 0f) return this
    val focusInWorld = planTransform(canvas, world, this, padding).toWorld(focus)
    val zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val scale = fitScale(canvas, world, padding) * zoom
    val content = world.scaled(scale)
    val origin = Offset(
        x = focus.x - focusInWorld.x.toFloat() * scale,
        y = focus.y - focusInWorld.y.toFloat() * scale,
    )
    val pan = (origin - centeredOrigin(canvas, content) + panChange)
        .clamped(canvas = canvas, content = content)
    return PlanViewport(zoom = zoom, pan = pan)
}

private fun PlanSize.scaled(scale: Float): Size = Size(
    width = width.toFloat() * scale,
    height = height.toFloat() * scale,
)

private fun centeredOrigin(canvas: Size, content: Size): Offset = Offset(
    x = (canvas.width - content.width) / 2f,
    y = (canvas.height - content.height) / 2f,
)

/** Pan that keeps the sheet on the canvas; an axis where the sheet fits stays centered. */
private fun Offset.clamped(canvas: Size, content: Size): Offset = Offset(
    x = clampedAxis(x, content.width, canvas.width),
    y = clampedAxis(y, content.height, canvas.height),
)

private fun clampedAxis(value: Float, content: Float, viewport: Float): Float {
    if (content <= viewport) return 0f
    val limit = (content - viewport) / 2f
    return value.coerceIn(-limit, limit)
}
