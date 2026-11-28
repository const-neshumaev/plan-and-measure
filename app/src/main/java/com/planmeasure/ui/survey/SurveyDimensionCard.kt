package com.planmeasure.ui.survey

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.ui.components.LengthInputField
import com.planmeasure.ui.plans.dimensionCardOffset
import com.planmeasure.units.LengthUnit
import kotlin.math.roundToInt

private val CardWidth = 300.dp
private val CardMargin = 8.dp
private val CardGap = 18.dp

/**
 * Input card of one line dimension, floating next to the line.
 *
 * A dimension of a survey is a label, not a scale: the card edits the value and the note, and
 * nothing about the sketch changes (ADR 0021). There is no Save button: the editor commits when
 * the card closes, when Done is pressed or when a rangefinder reading arrives.
 */
@Composable
internal fun SurveyDimensionCard(
    segmentId: String,
    value: String,
    comment: String,
    unit: LengthUnit,
    hasSavedDimension: Boolean,
    connected: Boolean,
    onValueChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(segmentId) {
        // With the rangefinder connected the value is expected from the device; without it manual
        // input is the only way, and a line without a dimension is usually measured right now.
        if (!connected && !hasSavedDimension) focusRequester.requestFocus()
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
                    text = stringResource(R.string.survey_dimension_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (hasSavedDimension) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.survey_dimension_delete),
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

            LengthInputField(
                value = value,
                unit = unit,
                onValueChange = onValueChange,
                label = stringResource(R.string.survey_dimension_value),
                placeholder = stringResource(R.string.measurement_value_hint),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .focusRequester(focusRequester),
                imeAction = ImeAction.Done,
                onDone = onDone,
            )

            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                label = { Text(stringResource(R.string.survey_dimension_comment)) },
                placeholder = { Text(stringResource(R.string.survey_dimension_comment_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .padding(end = 4.dp),
            )

            Text(
                text = stringResource(
                    if (connected) {
                        R.string.survey_dimension_hint_connected
                    } else {
                        R.string.survey_dimension_hint_disconnected
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/** Places the card next to its line inside the canvas, lifting it above the keyboard. */
@Composable
internal fun SurveyDimensionCardOverlay(
    anchor: Offset,
    canvasSize: IntSize,
    segmentId: String,
    value: String,
    comment: String,
    unit: LengthUnit,
    hasSavedDimension: Boolean,
    connected: Boolean,
    onValueChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
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

    SurveyDimensionCard(
        segmentId = segmentId,
        value = value,
        comment = comment,
        unit = unit,
        hasSavedDimension = hasSavedDimension,
        connected = connected,
        onValueChange = onValueChange,
        onCommentChange = onCommentChange,
        onDone = onDone,
        onDelete = onDelete,
        onClose = onClose,
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onSizeChanged { cardSize = it },
    )
}

/** Keeps touches on the card away from the canvas below it. */
private fun Modifier.blockCanvasTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
