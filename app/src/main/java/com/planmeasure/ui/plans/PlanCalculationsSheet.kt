package com.planmeasure.ui.plans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.plans.PlanCalculation
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanMeasurements
import com.planmeasure.ui.components.ListNameDialog

/**
 * List of the saved calculations of one plan.
 *
 * The values are shown here as a list, not as labels on the drawing: the plan itself stays a clean
 * sketch, and a measurement is something the user opens on purpose. Tapping a row's menu renames or
 * removes the calculation; the geometry is never touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanCalculationsSheet(
    plan: PlanDocument,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onCommentChange: (id: String, comment: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The id, not the object: the dialog must show the comment after it is edited in place.
    var viewingId by remember { mutableStateOf<String?>(null) }
    val viewing = viewingId?.let { id -> plan.calculations.firstOrNull { it.id == id } }
    var renaming by remember { mutableStateOf<PlanCalculation?>(null) }
    var deleting by remember { mutableStateOf<PlanCalculation?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_calculations),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.size(8.dp))
            if (plan.calculations.isEmpty()) {
                Text(
                    text = stringResource(R.string.plan_calculations_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.plan_calculations_empty_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(plan.calculations, key = { it.id }) { calculation ->
                        CalculationRow(
                            plan = plan,
                            calculation = calculation,
                            onView = { viewingId = calculation.id },
                            onRename = { renaming = calculation },
                            onDelete = { deleting = calculation },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = onNew,
                enabled = (plan.mmPerUnit ?: 0.0) > 0.0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calculate),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.plan_calculation_new))
            }
            if ((plan.mmPerUnit ?: 0.0) <= 0.0) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.plan_calculation_needs_scale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    viewing?.let { calculation ->
        PlanCalculationDetailsDialog(
            plan = plan,
            calculation = calculation,
            onCommentChange = { comment -> onCommentChange(calculation.id, comment) },
            onDismiss = { viewingId = null },
        )
    }

    renaming?.let { calculation ->
        ListNameDialog(
            title = stringResource(R.string.plan_calculation_rename_title),
            labelRes = R.string.plan_calculation_name,
            hintRes = R.string.plan_calculation_name_hint,
            initialName = calculation.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                renaming = null
                onRename(calculation.id, name)
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { calculation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.plan_calculation_delete_title)) },
            text = { Text(stringResource(R.string.plan_calculation_delete_text, calculation.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDelete(calculation.id)
                    },
                ) {
                    Text(stringResource(R.string.plan_calculation_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CalculationRow(
    plan: PlanDocument,
    calculation: PlanCalculation,
    onView: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val result = remember(plan, calculation) {
        PlanMeasurements.evaluate(plan, calculation)
    }
    val contour = result.contour
    val length = result.length

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = calculation.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                result.valid && contour != null -> {
                    val mark = if (result.mismatched) MISMATCH_MARK else ""
                    Text(
                        text = stringResource(
                            R.string.plan_calculation_area_meta,
                            mark + planAreaText(contour.netAreaSquareMillimeters, plan.unit),
                            mark + planLengthText(contour.perimeterMillimeters, plan.unit),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
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
                }
                result.valid && length != null -> {
                    val mark = if (result.mismatched) MISMATCH_MARK else ""
                    Text(
                        text = mark + planLengthText(length.lengthMillimeters, plan.unit),
                        style = MaterialTheme.typography.bodyMedium,
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
                else -> {
                    Text(
                        text = if (contour != null) {
                            stringResource(R.string.plan_calculation_invalid_net)
                        } else {
                            stringResource(R.string.plan_calculation_invalid)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (calculation.comment.isNotBlank()) {
                Text(
                    text = calculation.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_calculation_view)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_visibility),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onView()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_calculation_delete)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
