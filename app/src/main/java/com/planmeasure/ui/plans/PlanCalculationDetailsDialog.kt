package com.planmeasure.ui.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.plans.PlanCalculation
import com.planmeasure.plans.PlanCalculationKind
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanMeasurements

/**
 * Full details of a saved calculation.
 *
 * The list row has room for the main number only; an area also has an outline area, cut-outs and a
 * perimeter that the user needs when checking materials. Values are the same live measurements the
 * list uses, so the dialog never disagrees with the row.
 */
@Composable
internal fun PlanCalculationDetailsDialog(
    plan: PlanDocument,
    calculation: PlanCalculation,
    onCommentChange: (comment: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val result = remember(plan, calculation) {
        PlanMeasurements.evaluate(plan, calculation)
    }
    val contour = result.contour
    val length = result.length
    var editingComment by remember(calculation.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(calculation.name) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        when (calculation.kind) {
                            PlanCalculationKind.AREA -> R.string.plan_calculation_area
                            PlanCalculationKind.LENGTH -> R.string.plan_calculation_length
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.plan_calculation_sketch),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                PlanCalculationSketch(
                    plan = plan,
                    calculation = calculation,
                    contour = contour,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                when {
                    contour != null -> AreaDetails(plan, calculation, contour, result)
                    length != null -> LengthDetails(plan, result, length)
                    else -> Text(
                        text = stringResource(R.string.plan_calculation_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.size(12.dp))
                CommentSection(
                    comment = calculation.comment,
                    onEdit = { editingComment = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )

    if (editingComment) {
        CommentEditDialog(
            initialComment = calculation.comment,
            onConfirm = { comment ->
                editingComment = false
                onCommentChange(comment)
            },
            onDismiss = { editingComment = false },
        )
    }
}

@Composable
private fun CommentSection(
    comment: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.plan_calculation_comment),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEdit) {
            Text(
                stringResource(
                    if (comment.isBlank()) {
                        R.string.plan_calculation_comment_add
                    } else {
                        R.string.plan_calculation_comment_edit
                    },
                ),
            )
        }
    }
    Text(
        text = comment.ifBlank { stringResource(R.string.plan_calculation_comment_empty) },
        style = MaterialTheme.typography.bodyMedium,
        color = if (comment.isBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun CommentEditDialog(
    initialComment: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var comment by remember(initialComment) { mutableStateOf(initialComment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_calculation_comment_title)) },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(MAX_CALCULATION_COMMENT_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.plan_calculation_comment)) },
                placeholder = { Text(stringResource(R.string.plan_calculation_comment_hint)) },
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(comment.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AreaDetails(
    plan: PlanDocument,
    calculation: PlanCalculation,
    contour: PlanMeasurements.PlanContourMeasurement,
    result: PlanMeasurements.PlanCalculationResult,
) {
    val mark = if (result.mismatched) MISMATCH_MARK else ""
    // With subtraction off the cut-outs are not part of the result; the total is the whole outline.
    if (calculation.subtractHoles) {
        DetailRow(
            label = stringResource(R.string.plan_calculation_gross),
            value = planAreaText(contour.grossAreaSquareMillimeters, plan.unit),
        )
        if (contour.holes.isNotEmpty()) {
            DetailRow(
                label = stringResource(R.string.plan_calculation_holes, contour.holes.size),
                value = planAreaText(contour.holes.sumOf { it.areaSquareMillimeters }, plan.unit),
            )
            Text(
                text = stringResource(R.string.plan_calculation_holes_subtracted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    DetailRow(
        label = stringResource(R.string.plan_calculation_net),
        value = mark + planAreaText(contour.netAreaSquareMillimeters, plan.unit),
        emphasis = true,
    )
    DetailRow(
        label = stringResource(R.string.plan_calculation_perimeter),
        value = mark + planLengthText(contour.perimeterMillimeters, plan.unit),
    )
    Text(
        text = stringResource(
            R.string.plan_calculation_measured,
            contour.measuredEdges,
            contour.edgeCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (result.mismatched) {
        Text(
            text = pluralStringResource(
                R.plurals.plan_calculation_mismatch,
                contour.mismatchedEdges,
                contour.mismatchedEdges,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (contour.netAreaSquareMillimeters < 0.0) {
        Text(
            text = stringResource(R.string.plan_calculation_invalid_net),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun LengthDetails(
    plan: PlanDocument,
    result: PlanMeasurements.PlanCalculationResult,
    length: PlanMeasurements.PlanLengthMeasurement,
) {
    val mark = if (result.mismatched) MISMATCH_MARK else ""
    DetailRow(
        label = stringResource(R.string.plan_calculation_length),
        value = mark + planLengthText(length.lengthMillimeters, plan.unit),
        emphasis = true,
    )
    Text(
        text = pluralStringResource(
            R.plurals.plan_calculation_segments,
            length.edgeCount,
            length.edgeCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (result.mismatched) {
        Text(
            text = pluralStringResource(
                R.plurals.plan_calculation_mismatch,
                length.mismatchedEdges,
                length.mismatchedEdges,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    emphasis: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasis) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasis) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}
