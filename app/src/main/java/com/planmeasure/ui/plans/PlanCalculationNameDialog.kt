package com.planmeasure.ui.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.planmeasure.R

private const val MAX_NAME_LENGTH = 60
internal const val MAX_CALCULATION_COMMENT_LENGTH = 500

/**
 * Name and comment of a new calculation.
 *
 * The comment is entered here, before saving, so the user does not have to reopen the calculation
 * just to explain what was measured.
 */
@Composable
internal fun PlanCalculationNameDialog(
    title: String,
    initialName: String,
    initialComment: String = "",
    onConfirm: (name: String, comment: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var comment by remember(initialComment) { mutableStateOf(initialComment) }
    val trimmedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plan_calculation_name)) },
                    placeholder = { Text(stringResource(R.string.plan_calculation_name_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(MAX_CALCULATION_COMMENT_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plan_calculation_comment)) },
                    placeholder = { Text(stringResource(R.string.plan_calculation_comment_hint)) },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName, comment.trim()) },
                enabled = trimmedName.isNotEmpty(),
            ) {
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
