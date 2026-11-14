package com.planmeasure.ui.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.ble.BleUuids
import com.planmeasure.ble.ScanItem
import com.planmeasure.ui.components.BleAvailabilityGate

@Composable
fun ScanScreen(viewModel: LabViewModel) {
    val devices by viewModel.devices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val connectingAddress by viewModel.connectingAddress.collectAsState()
    val requestMtuOnConnect by viewModel.requestMtuOnConnect.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BleAvailabilityGate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (scanning) viewModel.stopScan() else viewModel.startScan()
                    },
                ) {
                    Text(
                        stringResource(
                            if (scanning) R.string.scan_stop else R.string.scan_start,
                        ),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Checkbox(
                    checked = requestMtuOnConnect,
                    onCheckedChange = viewModel::setRequestMtuOnConnect,
                )
                Text(
                    text = stringResource(R.string.scan_request_mtu),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = if (scanning) {
                    pluralStringResource(R.plurals.scan_scanning, devices.size, devices.size)
                } else {
                    pluralStringResource(R.plurals.scan_found, devices.size, devices.size)
                },
                style = MaterialTheme.typography.bodySmall,
            )

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.scan_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(devices, key = { it.address }) { item ->
                        DeviceCard(
                            item = item,
                            connecting = connectingAddress == item.address,
                            onConnect = { viewModel.connect(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    item: ScanItem,
    connecting: Boolean,
    onConnect: () -> Unit,
) {
    val rssiText = stringResource(
        if (item.connectable) R.string.scan_rssi else R.string.scan_rssi_not_connectable,
        item.rssi,
    )

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name ?: stringResource(R.string.scan_unnamed),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = item.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = rssiText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    Button(onClick = onConnect, enabled = item.connectable) {
                        Text(stringResource(R.string.scan_connect))
                    }
                }
            }
            if (item.serviceUuids.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.scan_services,
                        item.serviceUuids.joinToString(", ") { BleUuids.label(it) },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            item.manufacturer?.let { manufacturer ->
                Text(
                    text = stringResource(R.string.scan_manufacturer, manufacturer),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
