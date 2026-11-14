package com.planmeasure.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.ui.components.DemoBanner
import com.planmeasure.ui.components.SectionScaffold

@Composable
fun MeasurementsScreen(
    onBack: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    SectionScaffold(titleRes = R.string.section_measurements, onBack = onBack) {
        DemoBanner()
        LastReadingCard()
        Text(
            text = stringResource(R.string.measurements_recent),
            style = MaterialTheme.typography.titleMedium,
        )
        RecentMeasurements()
        Button(onClick = onOpenConnect, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.measurements_connect))
        }
    }
}

@Composable
private fun LastReadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.measurements_last),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "1.763",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = stringResource(R.string.measurements_unit_m),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
            }
            Text(
                text = "12:41",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecentMeasurements() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            MeasurementRow(value = "1.763", time = "12:41")
            HorizontalDivider()
            MeasurementRow(value = "3.281", time = "12:39")
            HorizontalDivider()
            MeasurementRow(value = "0.842", time = "12:37")
        }
    }
}

@Composable
private fun MeasurementRow(value: String, time: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.measurements_value, value),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
