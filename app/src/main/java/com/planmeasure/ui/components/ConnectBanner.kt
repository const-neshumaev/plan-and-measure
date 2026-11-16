package com.planmeasure.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.planmeasure.R

/**
 * Status of the rangefinder link: a Bluetooth glyph, a title, a hint and an optional action.
 *
 * The colours come from the caller because the same card means "recording" in a measurement list
 * and "readings go into the open dimension" in a plan.
 */
@Composable
fun ConnectBanner(
    title: String,
    hint: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
