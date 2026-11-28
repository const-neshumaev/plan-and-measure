package com.planmeasure.ui.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.plans.PlanCalculationKind
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanMeasurements

/**
 * Bottom panel of the temporary measurement mode: what to measure, the current result and the
 * actions that finish the selection.
 *
 * The panel replaces the drawing tools while the mode is active, so the segmented switch inside it
 * has only two options and never competes with the three drawing tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanCalculationPanel(
    plan: PlanDocument,
    kind: PlanCalculationKind,
    edgeIds: Set<String>,
    subtractHoles: Boolean,
    showHints: Boolean,
    onKindChange: (PlanCalculationKind) -> Unit,
    onSubtractHolesChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contour = remember(plan, edgeIds, subtractHoles) {
        if (edgeIds.isEmpty()) {
            null
        } else {
            PlanMeasurements.measureArea(plan, edgeIds, subtractHoles)
        }
    }
    val length = remember(plan, edgeIds) {
        if (edgeIds.isEmpty()) null else PlanMeasurements.measureLength(plan, edgeIds)
    }
    val canSave = when (kind) {
        PlanCalculationKind.AREA ->
            contour != null && contour.netAreaSquareMillimeters >= 0.0
        PlanCalculationKind.LENGTH -> length != null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PlanCalculationKind.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = kind == value,
                        onClick = { onKindChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PlanCalculationKind.entries.size,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                when (value) {
                                    PlanCalculationKind.AREA -> R.string.plan_calculation_area
                                    PlanCalculationKind.LENGTH -> R.string.plan_calculation_length
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            when (kind) {
                PlanCalculationKind.AREA -> AreaContent(
                    plan = plan,
                    contour = contour,
                    subtractHoles = subtractHoles,
                    showHints = showHints,
                    onSubtractHolesChange = onSubtractHolesChange,
                )
                PlanCalculationKind.LENGTH -> LengthContent(
                    plan = plan,
                    length = length,
                    showHints = showHints,
                )
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onReset,
                    enabled = edgeIds.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.plan_calculation_reset))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(Modifier.size(8.dp))
                Button(onClick = onSave, enabled = canSave) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun AreaContent(
    plan: PlanDocument,
    contour: PlanMeasurements.PlanContourMeasurement?,
    subtractHoles: Boolean,
    showHints: Boolean,
    onSubtractHolesChange: (Boolean) -> Unit,
) {
    if (contour == null) {
        if (showHints) {
            Text(
                text = stringResource(R.string.plan_calculation_area_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val mismatched = contour.mismatchedEdges > 0
    val mark = if (mismatched) MISMATCH_MARK else ""
    // With subtraction off the cut-outs are not part of the result, so showing their areas would
    // only raise questions. The total is the whole outline in that case.
    if (subtractHoles) {
        MeasurementRow(
            label = stringResource(R.string.plan_calculation_gross),
            value = planAreaText(contour.grossAreaSquareMillimeters, plan.unit),
        )
        if (contour.holes.isNotEmpty()) {
            MeasurementRow(
                label = stringResource(R.string.plan_calculation_holes, contour.holes.size),
                value = planAreaText(contour.holes.sumOf { it.areaSquareMillimeters }, plan.unit),
            )
        }
    }
    MeasurementRow(
        label = stringResource(R.string.plan_calculation_net),
        value = mark + planAreaText(contour.netAreaSquareMillimeters, plan.unit),
        emphasis = true,
    )
    MeasurementRow(
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
    if (contour.holes.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = subtractHoles,
                    onValueChange = onSubtractHolesChange,
                    role = Role.Switch,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = subtractHoles,
                onCheckedChange = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.plan_calculation_subtract_holes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (mismatched) {
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
private fun LengthContent(
    plan: PlanDocument,
    length: PlanMeasurements.PlanLengthMeasurement?,
    showHints: Boolean,
) {
    if (length == null) {
        if (showHints) {
            Text(
                text = stringResource(R.string.plan_calculation_length_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Text(
        text = pluralStringResource(
            R.plurals.plan_calculation_segments,
            length.edgeCount,
            length.edgeCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val mark = if (length.mismatchedEdges > 0) MISMATCH_MARK else ""
    MeasurementRow(
        label = stringResource(R.string.plan_calculation_length),
        value = mark + planLengthText(length.lengthMillimeters, plan.unit),
        emphasis = true,
    )
    if (length.mismatchedEdges > 0) {
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
private fun MeasurementRow(
    label: String,
    value: String,
    emphasis: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
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
