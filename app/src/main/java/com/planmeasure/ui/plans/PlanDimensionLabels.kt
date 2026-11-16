package com.planmeasure.ui.plans

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.hypot

/** One measured wall as it appears on screen: the projected edge and the size of its label. */
internal data class DimensionLabelCandidate(
    val edgeId: String,
    val start: Offset,
    val end: Offset,
    val size: Size,
)

/** A label that passed the visibility rules, given by its top-left corner. */
internal data class PlacedDimensionLabel(
    val edgeId: String,
    val topLeft: Offset,
)

/**
 * Chooses which dimension labels fit on the drawing.
 *
 * Many short walls side by side would turn the plan into unreadable text, so a label is skipped
 * when its wall is shorter on screen than the text, and when it would overlap a label that was
 * already placed. The selected wall is always labelled: its input card is open, and the user has
 * to see which value is being edited. Zooming in gives short walls more room, so hidden labels
 * come back without a separate control.
 *
 * [gap] is the distance between a label and its wall, [spacing] the free space kept between
 * neighbouring labels. Both are pixels.
 */
internal fun placeDimensionLabels(
    candidates: List<DimensionLabelCandidate>,
    selectedEdgeId: String?,
    gap: Float,
    spacing: Float,
): List<PlacedDimensionLabel> {
    if (candidates.isEmpty()) return emptyList()

    val ordered = candidates.sortedWith(
        compareBy<DimensionLabelCandidate> { it.edgeId != selectedEdgeId }
            .thenBy { it.start.y + it.end.y }
            .thenBy { it.start.x + it.end.x },
    )

    val placedRects = mutableListOf<Rect>()
    val placed = mutableListOf<PlacedDimensionLabel>()
    ordered.forEach { candidate ->
        val selected = candidate.edgeId == selectedEdgeId
        val length = (candidate.end - candidate.start).getDistance()
        if (!selected && length < candidate.size.width) return@forEach

        val center = labelCenter(candidate, gap)
        val topLeft = Offset(
            x = center.x - candidate.size.width / 2f,
            y = center.y - candidate.size.height / 2f,
        )
        val rect = Rect(topLeft, candidate.size)
        if (!selected && placedRects.any { it.overlaps(rect.inflate(spacing)) }) return@forEach

        placedRects += rect
        placed += PlacedDimensionLabel(edgeId = candidate.edgeId, topLeft = topLeft)
    }
    return placed
}

/**
 * Middle of the wall pushed sideways by [gap], so the label sits beside its wall instead of on it.
 *
 * The normal is flipped to the upper half of the screen: labels of horizontal walls then read above
 * the line, which is where the eye looks for a dimension.
 */
private fun labelCenter(candidate: DimensionLabelCandidate, gap: Float): Offset {
    val dx = candidate.end.x - candidate.start.x
    val dy = candidate.end.y - candidate.start.y
    val length = hypot(dx, dy)
    val normal = if (length == 0f) {
        Offset(0f, -1f)
    } else {
        val nx = -dy / length
        val ny = dx / length
        if (ny > 0f) Offset(-nx, -ny) else Offset(nx, ny)
    }
    val distance = candidate.size.height / 2f + gap
    return Offset(
        x = (candidate.start.x + candidate.end.x) / 2f + normal.x * distance,
        y = (candidate.start.y + candidate.end.y) / 2f + normal.y * distance,
    )
}
