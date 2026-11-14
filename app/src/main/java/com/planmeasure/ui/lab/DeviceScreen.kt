package com.planmeasure.ui.lab

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.ble.BleUuids
import com.planmeasure.ble.GattCharInfo
import com.planmeasure.ble.GattServiceInfo
import com.planmeasure.ble.GattState
import com.planmeasure.logging.Hex
import com.planmeasure.logging.LogEntry
import com.planmeasure.logging.LogKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun DeviceScreen(viewModel: LabViewModel) {
    val context = LocalContext.current
    val gattState by viewModel.gattState.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.device_log_copied)

    var selectedTab by remember { mutableStateOf(0) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var showMarkerDialog by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) saveText(context, uri, content)
    }
    val textLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) saveText(context, uri, content)
    }

    Column(Modifier.fillMaxSize()) {
        StatusRow(gattState)

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.device_tab_gatt)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.device_tab_log, entries.size)) },
            )
        }

        when (selectedTab) {
            0 -> GattTab(viewModel, gattState)
            else -> LogTab(
                entries = entries,
                onMarker = { showMarkerDialog = true },
                onExportCsv = {
                    pendingExport = viewModel.exportCsv()
                    csvLauncher.launch(exportFileName("ble-log", "csv"))
                },
                onExportGatt = {
                    pendingExport = viewModel.exportGattDump()
                    textLauncher.launch(exportFileName("gatt-dump", "txt"))
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(viewModel.exportLogText()))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                },
                onClear = { viewModel.clearLog() },
            )
        }
    }

    if (showMarkerDialog) {
        MarkerDialog(
            onDismiss = { showMarkerDialog = false },
            onConfirm = { text ->
                viewModel.addMarker(text)
                showMarkerDialog = false
            },
        )
    }
}

@Composable
private fun StatusRow(gattState: GattState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = if (gattState.connected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = stringResource(
                    if (gattState.connected) {
                        R.string.device_connected
                    } else {
                        R.string.device_disconnected
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.device_mtu, gattState.mtu),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = pluralStringResource(
                R.plurals.device_services,
                gattState.services.size,
                gattState.services.size,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GattTab(viewModel: LabViewModel, gattState: GattState) {
    val writable = remember(gattState.services) { gattState.writableCharacteristics }
    val emptyTextError = stringResource(R.string.device_error_empty_text)
    val invalidHexError = stringResource(R.string.device_error_invalid_hex)

    var selectedChar by remember { mutableStateOf<UUID?>(null) }
    var input by remember { mutableStateOf("") }
    var textMode by remember { mutableStateOf(false) }
    var appendCrLf by remember { mutableStateOf(true) }
    var withResponse by remember { mutableStateOf(true) }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(writable) {
        if (writable.none { it.uuid == selectedChar }) {
            selectedChar = writable.firstOrNull()?.uuid
        }
    }

    fun send() {
        val chUuid = selectedChar ?: return
        val bytes = if (textMode) {
            val base = input.toByteArray(Charsets.UTF_8)
            if (appendCrLf) base + byteArrayOf(0x0D, 0x0A) else base
        } else {
            Hex.parse(input)
        }
        if (bytes == null || bytes.isEmpty()) {
            inputError = if (textMode) emptyTextError else invalidHexError
            return
        }
        inputError = null
        viewModel.write(chUuid, bytes, withResponse)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            WriteCard(
                writable = writable,
                selectedChar = selectedChar,
                onSelectChar = { selectedChar = it },
                input = input,
                onInputChange = { input = it },
                textMode = textMode,
                onTextModeChange = { textMode = it },
                appendCrLf = appendCrLf,
                onAppendCrLfChange = { appendCrLf = it },
                withResponse = withResponse,
                onWithResponseChange = { withResponse = it },
                inputError = inputError,
                onSend = { send() },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.subscribeAll() }) {
                    Text(stringResource(R.string.device_subscribe_all))
                }
                OutlinedButton(onClick = { viewModel.readAll() }) {
                    Text(stringResource(R.string.device_read_all))
                }
            }
        }

        gattState.services.forEach { service ->
            item(key = "service-${service.uuid}") {
                ServiceRow(service)
            }
            items(service.characteristics, key = { "char-${service.uuid}-${it.uuid}" }) { characteristic ->
                CharacteristicRow(characteristic, viewModel)
            }
        }
    }
}

@Composable
private fun WriteCard(
    writable: List<GattCharInfo>,
    selectedChar: UUID?,
    onSelectChar: (UUID) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    textMode: Boolean,
    onTextModeChange: (Boolean) -> Unit,
    appendCrLf: Boolean,
    onAppendCrLfChange: (Boolean) -> Unit,
    withResponse: Boolean,
    onWithResponseChange: (Boolean) -> Unit,
    inputError: String?,
    onSend: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.device_write_title),
                style = MaterialTheme.typography.titleSmall,
            )

            if (writable.isEmpty()) {
                Text(
                    text = stringResource(R.string.device_no_writable),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                writable.forEach { characteristic ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChar(characteristic.uuid) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = characteristic.uuid == selectedChar,
                            onClick = { onSelectChar(characteristic.uuid) },
                        )
                        Text(
                            text = BleUuids.label(characteristic.uuid),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !textMode, onClick = { onTextModeChange(false) })
                    Text(
                        text = stringResource(R.string.device_hex),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = textMode, onClick = { onTextModeChange(true) })
                    Text(
                        text = stringResource(R.string.device_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            stringResource(
                                if (textMode) {
                                    R.string.device_payload_text
                                } else {
                                    R.string.device_payload_hex
                                },
                            ),
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                if (textMode) {
                                    R.string.device_payload_text_hint
                                } else {
                                    R.string.device_payload_hex_hint
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    isError = inputError != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )

                if (inputError != null) {
                    Text(
                        text = inputError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (textMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = appendCrLf, onCheckedChange = onAppendCrLfChange)
                        Text(
                            text = stringResource(R.string.device_append_crlf),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withResponse, onCheckedChange = onWithResponseChange)
                    Text(
                        text = stringResource(R.string.device_write_with_response),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = onSend,
                    enabled = selectedChar != null && input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.device_send))
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(service: GattServiceInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = BleUuids.label(service.uuid),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = service.uuid.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CharacteristicRow(characteristic: GattCharInfo, viewModel: LabViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = BleUuids.label(characteristic.uuid),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = characteristic.uuid.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
            if (characteristic.propertiesLabel.isNotEmpty()) {
                Text(
                    text = characteristic.propertiesLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (characteristic.descriptorUuids.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.device_descriptors,
                        characteristic.descriptorUuids.joinToString(", ") {
                            BleUuids.label(it)
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (characteristic.canRead) {
                    TextButton(onClick = { viewModel.read(characteristic.uuid) }) {
                        Text(stringResource(R.string.device_read))
                    }
                }
                if (characteristic.canSubscribe) {
                    Text(
                        text = stringResource(
                            if (characteristic.notifying) {
                                R.string.device_notifying
                            } else {
                                R.string.device_not_notifying
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = characteristic.notifying,
                        onCheckedChange = {
                            viewModel.setNotifications(characteristic.uuid, it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogTab(
    entries: List<LogEntry>,
    onMarker: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGatt: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.scrollToItem(entries.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onMarker) { Text(stringResource(R.string.device_marker)) }
            OutlinedButton(onClick = onExportCsv) { Text(stringResource(R.string.device_export_csv)) }
            OutlinedButton(onClick = onExportGatt) { Text(stringResource(R.string.device_export_gatt)) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.device_copy_log)) }
            OutlinedButton(onClick = onClear) { Text(stringResource(R.string.device_clear)) }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(8.dp),
        ) {
            items(entries, key = { it.seq }) { entry ->
                LogRow(entry)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.kind) {
        LogKind.ERROR -> MaterialTheme.colorScheme.error
        LogKind.MARKER -> MaterialTheme.colorScheme.primary
        LogKind.NOTIFY -> MaterialTheme.colorScheme.tertiary
        LogKind.WRITE -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = "#${entry.seq} ${entry.clock} +${entry.elapsedMs}ms " +
                    "${entry.kind.name} ${entry.source} - ${entry.message}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = color,
            )
            entry.hex?.let { hex ->
                Text(
                    text = hex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            entry.ascii?.let { ascii ->
                Text(
                    text = "|$ascii|",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun MarkerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val defaultMarker = stringResource(R.string.device_marker_default)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_marker_title)) },
        text = {
            Column {
                Text(stringResource(R.string.device_marker_text))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.device_marker_label)) },
                    placeholder = { Text(stringResource(R.string.device_marker_hint)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifBlank { defaultMarker }) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun exportFileName(prefix: String, extension: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "$prefix-$stamp.$extension"
}

private fun saveText(context: Context, uri: Uri, text: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(context, context.getString(R.string.device_saved), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.device_save_failed, e.message ?: ""),
            Toast.LENGTH_LONG,
        ).show()
    }
}
