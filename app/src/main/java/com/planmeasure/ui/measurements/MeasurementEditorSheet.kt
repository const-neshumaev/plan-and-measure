package com.planmeasure.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.measurements.LengthUnit
import com.planmeasure.measurements.Measurement
import com.planmeasure.measurements.MeasurementInput

private const val MAX_VALUE_LENGTH = 12
private const val MAX_COMMENT_LENGTH = 200

/**
 * Bottom sheet for adding or editing one measurement.
 *
 * In add mode the sheet stays open after each save so that several values can be typed without
 * reopening it; the unit and the focus stay in place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeasurementEditorSheet(
    editing: Measurement?,
    initialUnit: LengthUnit,
    focusComment: Boolean,
    onDismiss: () -> Unit,
    onAdd: (valueText: String, unit: LengthUnit, comment: String) -> Unit,
    onUpdate: (valueText: String, unit: LengthUnit, comment: String) -> Unit,
    onUnitChanged: (LengthUnit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by rememberSaveable(editing?.id) { mutableStateOf(editing?.valueText.orEmpty()) }
    var comment by rememberSaveable(editing?.id) { mutableStateOf(editing?.comment.orEmpty()) }
    var unitName by rememberSaveable(editing?.id) {
        mutableStateOf((editing?.unit ?: initialUnit).name)
    }
    val unit = LengthUnit.valueOf(unitName)
    val normalized = MeasurementInput.normalize(value)
    val showError = value.isNotBlank() && normalized == null
    val valueFocusRequester = remember { FocusRequester() }
    val commentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (editing != null && focusComment) {
            commentFocusRequester.requestFocus()
        } else {
            valueFocusRequester.requestFocus()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (editing == null) R.string.measurement_add else R.string.measurement_edit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(MAX_VALUE_LENGTH) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(valueFocusRequester),
                label = { Text(stringResource(R.string.measurement_value_label)) },
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
                    imeAction = ImeAction.Next,
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.measurement_unit_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LengthUnit.entries.forEach { candidate ->
                        FilterChip(
                            selected = unit == candidate,
                            onClick = {
                                unitName = candidate.name
                                onUnitChanged(candidate)
                            },
                            label = { Text(stringResource(candidate.labelRes())) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(MAX_COMMENT_LENGTH) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(commentFocusRequester),
                label = { Text(stringResource(R.string.measurement_comment_label)) },
                placeholder = { Text(stringResource(R.string.measurement_comment_hint)) },
                minLines = 2,
                maxLines = 4,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing == null) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                    Button(
                        onClick = {
                            val text = normalized ?: return@Button
                            onAdd(text, unit, comment)
                            value = ""
                            comment = ""
                            valueFocusRequester.requestFocus()
                        },
                        enabled = normalized != null,
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val text = normalized ?: return@Button
                            onUpdate(text, unit, comment)
                        },
                        enabled = normalized != null,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
