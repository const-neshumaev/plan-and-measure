package com.planmeasure.ui.connect

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.ble.GattState
import com.planmeasure.ble.ScanItem
import com.planmeasure.ble.looksLikeRangefinder
import com.planmeasure.ui.components.BleAvailabilityGate

/**
 * User-facing connection flow: finds nearby devices, highlights the rangefinder and lets the
 * user connect or disconnect. The BLE session itself is owned by [LabViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(viewModel: LabViewModel, onDone: () -> Unit) {
    val gattState by viewModel.gattState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val connectingAddress by viewModel.connectingAddress.collectAsState()
    val reconnecting by viewModel.reconnecting.collectAsState()
    val lastDevice by viewModel.lastDevice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(onBack = onDone)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connect_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (gattState.connected) {
                ConnectedContent(
                    state = gattState,
                    onDone = onDone,
                    onDisconnect = viewModel::disconnect,
                )
            } else {
                BleAvailabilityGate {
                    DiscoveryContent(
                        viewModel = viewModel,
                        devices = devices,
                        scanning = scanning,
                        connectingAddress = connectingAddress,
                        reconnecting = reconnecting,
                        lastDeviceName = lastDevice?.name,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryContent(
    viewModel: LabViewModel,
    devices: List<ScanItem>,
    scanning: Boolean,
    connectingAddress: String?,
    reconnecting: Boolean,
    lastDeviceName: String?,
) {
    // connect() stops scanning; resume discovery when an attempt finishes without a connection.
    LaunchedEffect(connectingAddress) {
        if (connectingAddress == null) viewModel.startScan()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }

    val visibleDevices = remember(devices) {
        devices
            .filter { it.connectable }
            .filter { it.looksLikeRangefinder() || it.rssi >= MIN_OTHER_DEVICE_RSSI }
    }
    val rangefinders = visibleDevices.filter { it.looksLikeRangefinder() }
    val otherDevices = visibleDevices.filterNot { it.looksLikeRangefinder() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.connect_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (reconnecting) {
            RestoringBanner(
                deviceName = lastDeviceName ?: stringResource(R.string.device_unknown),
                onCancel = viewModel::cancelReconnect,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = stringResource(R.string.connect_scanning, visibleDevices.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (visibleDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.connect_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (rangefinders.isNotEmpty()) {
                    item(key = "header-rangefinder") {
                        SectionLabel(
                            titleRes = R.string.connect_section_rangefinder,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(rangefinders, key = { it.address }) { item ->
                        DeviceRow(
                            item = item,
                            connecting = connectingAddress == item.address,
                            enabled = connectingAddress == null,
                            onConnect = { viewModel.connect(item) },
                        )
                    }
                }
                if (otherDevices.isNotEmpty()) {
                    item(key = "header-other") {
                        SectionLabel(titleRes = R.string.connect_section_other)
                    }
                    items(otherDevices, key = { it.address }) { item ->
                        DeviceRow(
                            item = item,
                            connecting = connectingAddress == item.address,
                            enabled = connectingAddress == null,
                            onConnect = { viewModel.connect(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoringBanner(deviceName: String, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.connect_reconnecting, deviceName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.connect_reconnect_cancel))
            }
        }
    }
}

@Composable
private fun SectionLabel(
    @StringRes titleRes: Int,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DeviceRow(
    item: ScanItem,
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    val rangefinder = item.looksLikeRangefinder()
    val container = if (rangefinder) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (rangefinder) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val details = stringResource(
        if (rangefinder) R.string.connect_rangefinder_signal else R.string.connect_signal,
        item.rssi,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (rangefinder) {
                        R.drawable.ic_section_measurements
                    } else {
                        R.drawable.ic_bluetooth
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.name ?: stringResource(R.string.scan_unnamed),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Button(onClick = onConnect, enabled = enabled) {
                    Text(stringResource(R.string.scan_connect))
                }
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    state: GattState,
    onDone: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bluetooth),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.connect_connected_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = state.deviceName ?: stringResource(R.string.device_unknown),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                state.deviceAddress?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    text = stringResource(R.string.connect_connected_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connect_done))
        }
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connect_disconnect))
        }
    }
}

private const val MIN_OTHER_DEVICE_RSSI = -90
