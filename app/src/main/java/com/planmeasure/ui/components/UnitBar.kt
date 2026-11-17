package com.planmeasure.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.units.LengthUnit

/** Short label of a length unit as shown in the interface. */
fun LengthUnit.labelRes(): Int = when (this) {
    LengthUnit.METERS -> R.string.unit_meters
    LengthUnit.CENTIMETERS -> R.string.unit_centimeters
    LengthUnit.MILLIMETERS -> R.string.unit_millimeters
    LengthUnit.FEET -> R.string.unit_feet
    LengthUnit.INCHES -> R.string.unit_inches
}

/**
 * Unit of the open document: one compact bar under the connection status.
 *
 * The same bar is used by measurement lists and plans so that the two sections behave alike.
 * Changing the unit only changes how the stored millimeters are shown (ADR 0009), which is why
 * the menu can apply at once without a confirmation.
 */
@Composable
fun UnitBar(
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.unit_bar, stringResource(unit.labelRes())),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LengthUnit.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(stringResource(candidate.labelRes())) },
                    leadingIcon = {
                        RadioButton(
                            selected = candidate == unit,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onUnitChange(candidate)
                    },
                )
            }
        }
    }
}
