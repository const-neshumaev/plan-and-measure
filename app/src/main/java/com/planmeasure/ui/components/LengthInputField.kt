package com.planmeasure.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.planmeasure.units.LengthInput
import com.planmeasure.units.LengthUnit

/**
 * One value field for every manually entered length.
 *
 * The field masks input with [LengthInput.filter] and shows the unit as a suffix, so the mask, the
 * keyboard type and the unit label stay in one place: measurement lists, plan dimensions and any
 * later object behave alike. [onValueChange] receives already masked text.
 *
 * The caller owns the focus requester and the surrounding layout through [modifier].
 */
@Composable
fun LengthInputField(
    value: String,
    unit: LengthUnit,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(LengthInput.filter(it, unit)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        suffix = { Text(stringResource(unit.labelRes())) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        keyboardActions = if (onDone != null) {
            KeyboardActions(onDone = { onDone() })
        } else {
            KeyboardActions.Default
        },
    )
}
