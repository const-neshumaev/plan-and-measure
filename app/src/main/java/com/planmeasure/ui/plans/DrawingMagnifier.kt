package com.planmeasure.ui.plans

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/** Diameter of the loupe. It has to hold a fingertip-sized patch of the drawing and stay clear. */
internal val MagnifierDiameter = 112.dp

/** Gap between the fingertip and the loupe, so the loupe is not covered by the finger. */
internal val MagnifierGap = 24.dp

/** Fixed magnification: one grid cell or wall joint is already comfortable at double size. */
internal const val MagnifierZoom = 2f

private val MagnifierShadowSpread = 6.dp
private const val MAGNIFIER_FADE_MILLIS = 100

/**
 * Display-only state of the drawing loupe.
 *
 * The gesture handler writes [touch], the canvas reads [alpha] and [anchor]; nothing here decides
 * what is drawn. [anchor] keeps the last touch position, so the loupe fades out where the finger
 * left the screen instead of jumping to the origin.
 */
@Stable
internal class MagnifierState {

    var touch by mutableStateOf<Offset?>(null)

    var anchor by mutableStateOf(Offset.Zero)
        internal set

    val alpha = Animatable(0f)
}

/** State of the loupe with its fade-in on the first touch and fade-out on release. */
@Composable
internal fun rememberMagnifierState(): MagnifierState {
    val state = remember { MagnifierState() }
    LaunchedEffect(state.touch) {
        val touch = state.touch
        if (touch == null) {
            if (state.alpha.value != 0f) state.alpha.animateTo(0f, tween(MAGNIFIER_FADE_MILLIS))
        } else {
            state.anchor = touch
            if (state.alpha.value != 1f) state.alpha.animateTo(1f, tween(MAGNIFIER_FADE_MILLIS))
        }
    }
    return state
}

/**
 * Centre of the loupe for [anchor]: above the finger, or below it when the top edge is too close.
 *
 * The circle stays inside [canvas]. A canvas smaller than the loupe clamps it to the first case
 * instead of failing, because the frame is clipped by the canvas anyway.
 */
internal fun magnifierCenter(anchor: Offset, canvas: Size, radius: Float, gap: Float): Offset {
    val above = anchor.y - radius - gap
    val y = if (above - radius >= 0f) above else anchor.y + radius + gap
    return Offset(
        x = anchor.x.coerceIn(radius, (canvas.width - radius).coerceAtLeast(radius)),
        y = y.coerceIn(radius, (canvas.height - radius).coerceAtLeast(radius)),
    )
}

/**
 * Draws the loupe rim, shadow and crosshair, and repeats [content] magnified around [anchor].
 *
 * [content] draws the same scene as the main canvas with the same transform: the canvas scale
 * grows walls, nodes and photo together, so the loupe shows them exactly as the drawing does.
 * The whole frame is composited through a layer only while it fades, so a settled loupe costs no
 * extra layer.
 */
internal fun DrawScope.drawMagnifier(
    anchor: Offset,
    center: Offset,
    radius: Float,
    alpha: Float,
    zoom: Float,
    fillColor: Color,
    rimColor: Color,
    shadowColor: Color,
    crosshairColor: Color,
    content: DrawScope.() -> Unit,
) {
    val spread = MagnifierShadowSpread.toPx()
    drawCircle(
        color = shadowColor.copy(alpha = 0.10f * alpha),
        radius = radius + spread,
        center = center,
    )
    drawCircle(
        color = shadowColor.copy(alpha = 0.10f * alpha),
        radius = radius + spread / 2f,
        center = center,
    )

    val layered = alpha < 1f
    if (layered) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(
                Rect(
                    left = center.x - radius - spread,
                    top = center.y - radius - spread,
                    right = center.x + radius + spread,
                    bottom = center.y + radius + spread,
                ),
                Paint().apply { this.alpha = alpha },
            )
        }
    }

    val circle = Path().apply {
        addOval(
            Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius,
            ),
        )
    }
    clipPath(circle) {
        drawCircle(color = fillColor, radius = radius, center = center)
        withTransform({
            translate(center.x, center.y)
            scale(zoom, zoom, pivot = Offset.Zero)
            translate(-anchor.x, -anchor.y)
        }) {
            content()
        }
    }
    drawCircle(
        color = rimColor.copy(alpha = 0.85f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawCrosshair(center, crosshairColor)

    if (layered) drawIntoCanvas { it.restore() }
}

/** Four short arms around the magnified point: they mark the exact touch without hiding it. */
private fun DrawScope.drawCrosshair(center: Offset, color: Color) {
    val arm = 10.dp.toPx()
    val gap = 2.dp.toPx()
    val width = 1.5.dp.toPx()
    listOf(
        Offset(-arm, 0f) to Offset(-gap, 0f),
        Offset(gap, 0f) to Offset(arm, 0f),
        Offset(0f, -arm) to Offset(0f, -gap),
        Offset(0f, gap) to Offset(0f, arm),
    ).forEach { (start, end) ->
        drawLine(
            color = color,
            start = center + start,
            end = center + end,
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color = color, radius = 1.2.dp.toPx(), center = center)
}
