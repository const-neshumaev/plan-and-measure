package com.planmeasure.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.measurements.Measurement
import com.planmeasure.units.Length
import com.planmeasure.units.LengthInput
import com.planmeasure.units.LengthUnit
import com.planmeasure.ui.components.LengthInputField

private const val MAX_COMMENT_LENGTH = 200

/**
 * Bottom sheet for adding or editing one measurement.
 *
 * The sheet works in the unit of the open list; changing that unit is the job of the bar above the
 * list, so the sheet stays compact. Adding a value closes the sheet, so the next value is entered
 * from a fresh empty field.
 *
 * The typed text is handed over as is: the ViewModel canonicalizes it once in the list's unit.
 * Converting here as well would apply the unit twice.
 *
 * The value field is [LengthInputField]: it masks input that cannot belong to a length in the
 * list's unit instead of turning the field red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEditorSheet(
    editing: Measurement?,
    unit: LengthUnit,
    focusComment: Boolean,
    onDismiss: () -> Unit,
    onAdd: (valueText: String, comment: String) -> Unit,
    onUpdate: (valueText: String, comment: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by rememberSaveable(editing?.id) {
        mutableStateOf(editing?.let { Length.format(it.valueMm, unit) }.orEmpty())
    }
    var comment by rememberSaveable(editing?.id) { mutableStateOf(editing?.comment.orEmpty()) }
    val normalized = LengthInput.normalize(value, unit)
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

            LengthInputField(
                value = value,
                unit = unit,
                onValueChange = { value = it },
                label = stringResource(R.string.measurement_value_label),
                placeholder = stringResource(R.string.measurement_value_hint),
                modifier = Modifier.focusRequester(valueFocusRequester),
            )

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
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            if (normalized == null) return@Button
                            onAdd(value, comment)
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
                            if (normalized == null) return@Button
                            onUpdate(value, comment)
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
