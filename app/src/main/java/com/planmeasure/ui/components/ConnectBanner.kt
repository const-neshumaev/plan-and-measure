package com.planmeasure.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planmeasure.R

/**
 * Compact status of the rangefinder link: a Bluetooth glyph, one line of text and an optional
 * action.
 *
 * A measurement list and a plan show the same card, so its height must not depend on the screen or
 * on the presence of an action: the row always keeps the minimum touch height. The colours come
 * from the caller because the same card means "recording" in a list and "readings go into the open
 * dimension" in a plan.
 */
@Composable
fun ConnectBanner(
    title: String,
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
