package com.planmeasure.ui.plans

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.measurements.LengthUnit
import com.planmeasure.measurements.MeasurementInput
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanPoint
import com.planmeasure.ui.measurements.labelRes
import kotlin.math.roundToInt

private val CardWidth = 300.dp
private val CardMargin = 8.dp

/** Distance from the wall to the card, so the card does not hide the line it belongs to. */
private val CardGap = 18.dp

private const val MAX_VALUE_LENGTH = 12

/**
 * Screen position of the middle of [edgeId], used to place the dimension card next to its wall.
 *
 * [padding] is the margin the canvas keeps around the fitted sheet, the same one [PlanCanvas]
 * draws with, otherwise the card would not follow the line during a pinch.
 */
internal fun dimensionCardAnchor(
    canvasSize: IntSize,
    plan: PlanDocument,
    edgeId: String,
    viewport: PlanViewport,
    padding: Float,
): Offset? {
    if (canvasSize.width == 0 || canvasSize.height == 0) return null
    val edge = plan.edges.firstOrNull { it.id == edgeId } ?: return null
    val nodes = plan.nodes.associateBy { it.id }
    val start = nodes[edge.startNodeId] ?: return null
    val end = nodes[edge.endNodeId] ?: return null
    val transform = planTransform(
        canvas = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        world = plan.world,
        viewport = viewport,
        padding = padding,
    )
    val startPx = transform.toPx(PlanPoint(start.x, start.y))
    val endPx = transform.toPx(PlanPoint(end.x, end.y))
    return Offset(
        x = (startPx.x + endPx.x) / 2f,
        y = (startPx.y + endPx.y) / 2f,
    )
}

/**
 * Position of the dimension card inside the canvas.
 *
 * The card is centred over the wall and prefers the space above it; near the top border it flips
 * below. [visibleBottom] is the part of the canvas left free by the keyboard: the card is pulled
 * above it instead of hiding under it. [margin] and [gap] are pixels.
 */
internal fun dimensionCardOffset(
    anchor: Offset,
    card: Size,
    canvas: Size,
    visibleBottom: Float,
    margin: Float,
    gap: Float,
): Offset {
    val lowestX = (canvas.width - card.width - margin).coerceAtLeast(margin)
    val x = (anchor.x - card.width / 2f).coerceIn(margin, lowestX)

    val lowestY = (visibleBottom - card.height - margin).coerceAtLeast(margin)
    val above = anchor.y - gap - card.height
    val below = anchor.y + gap
    val y = when {
        above >= margin -> above.coerceAtMost(lowestY)
        below <= lowestY -> below
        else -> lowestY
    }
    return Offset(x, y)
}

/**
 * Input card of one wall dimension, floating next to the wall (ADR 0008).
 *
 * There is no Save button: the editor commits the value when the card closes, when the keyboard's
 * Done is pressed or when a rangefinder reading arrives. [hasSavedDimension] only decides whether
 * the field takes focus and shows the delete action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanDimensionCard(
    edgeId: String,
    value: String,
    unit: LengthUnit,
    hasSavedDimension: Boolean,
    connected: Boolean,
    onValueChange: (String) -> Unit,
    onUnitChange: (LengthUnit) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = MeasurementInput.normalize(value)
    val showError = value.isNotBlank() && normalized == null
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(edgeId) {
        // A wall without a dimension is usually measured right now, so the keyboard opens at once.
        // An existing value is more often looked at or corrected, and the card should not cover
        // half of the sheet before the user asks for the keyboard.
        if (!hasSavedDimension) focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .width(CardWidth)
            .blockCanvasTouches(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.plan_dimension_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (hasSavedDimension) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.plan_dimension_delete),
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.take(MAX_VALUE_LENGTH)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp)
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.plan_dimension_value)) },
                placeholder = { Text(stringResource(R.string.measurement_value_hint)) },
                suffix = { Text(stringResource(unit.labelRes())) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.measurement_value_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
            )

            FlowRow(
                modifier = Modifier.padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LengthUnit.entries.forEach { candidate ->
                    FilterChip(
                        selected = unit == candidate,
                        onClick = { onUnitChange(candidate) },
                        label = { Text(stringResource(candidate.labelRes())) },
                    )
                }
            }

            Text(
                text = stringResource(
                    if (connected) {
                        R.string.plan_dimension_hint_connected
                    } else {
                        R.string.plan_dimension_hint_disconnected
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/**
 * Places the card next to its wall inside the canvas, lifting it above the keyboard.
 *
 * The IME inset is measured from the bottom of the window, while the canvas ends above the tool
 * row, so the card is lifted a little more than strictly needed. That only moves it away from the
 * keyboard, which is the safe direction.
 */
@Composable
internal fun PlanDimensionCardOverlay(
    anchor: Offset,
    canvasSize: IntSize,
    edgeId: String,
    value: String,
    unit: LengthUnit,
    hasSavedDimension: Boolean,
    connected: Boolean,
    onValueChange: (String) -> Unit,
    onUnitChange: (LengthUnit) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val imeBottom = WindowInsets.ime.getBottom(density).toFloat()
    val visibleBottom = (canvasSize.height - imeBottom).coerceAtLeast(0f)
    val margin = with(density) { CardMargin.toPx() }
    val gap = with(density) { CardGap.toPx() }

    val offset = dimensionCardOffset(
        anchor = anchor,
        card = Size(cardSize.width.toFloat(), cardSize.height.toFloat()),
        canvas = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        visibleBottom = visibleBottom,
        margin = margin,
        gap = gap,
    )

    PlanDimensionCard(
        edgeId = edgeId,
        value = value,
        unit = unit,
        hasSavedDimension = hasSavedDimension,
        connected = connected,
        onValueChange = onValueChange,
        onUnitChange = onUnitChange,
        onDone = onDone,
        onDelete = onDelete,
        onClose = onClose,
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onSizeChanged { cardSize = it },
    )
}

/**
 * Keeps touches on the card away from the canvas below it.
 *
 * The card is a sibling of the canvas, and a layout without pointer handling lets touches fall
 * through; without this the plan would select and pan while the user types.
 */
private fun Modifier.blockCanvasTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // The down has to be consumed as well: the canvas waits for an unconsumed down and would
        // otherwise start its own gesture on the same finger.
        down.consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
