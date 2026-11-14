package com.planmeasure

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planmeasure.ble.BleGattClient
import com.planmeasure.ble.BleScanner
import com.planmeasure.ble.BleUuids
import com.planmeasure.ble.GattState
import com.planmeasure.ble.ScanItem
import com.planmeasure.logging.LogExporter
import com.planmeasure.logging.LogKind
import com.planmeasure.logging.PacketLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LabScreen {
    Scan,
    Device,
}

class LabViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Application = app

    private val logDirectory: File = app.getExternalFilesDir(null) ?: app.filesDir
    private val logFile: File = File(logDirectory, "ble-log.txt")
    private val gattFile: File = File(logDirectory, "gatt.txt")

    private val packetLog = PacketLog(logFile)
    private val scanner = BleScanner(app, packetLog)
    private val client = BleGattClient(app, packetLog)

    init {
        packetLog.add(LogKind.SYSTEM, "APP", "Session started; log file: ${logFile.absolutePath}")
        viewModelScope.launch {
            client.state.collect { state ->
                if (state.services.isNotEmpty()) {
                    runCatching { gattFile.writeText(buildGattDump(state)) }
                }
            }
        }
    }

    val entries = packetLog.entries
    val gattState = client.state

    private val _screen = MutableStateFlow(LabScreen.Scan)
    val screen = _screen.asStateFlow()

    private val _devices = MutableStateFlow<List<ScanItem>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    private val _connectingAddress = MutableStateFlow<String?>(null)
    val connectingAddress = _connectingAddress.asStateFlow()

    private val _requestMtuOnConnect = MutableStateFlow(true)
    val requestMtuOnConnect = _requestMtuOnConnect.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar = _snackbar.asSharedFlow()

    private var scanJob: Job? = null

    val adapter: BluetoothAdapter? get() = scanner.adapter

    private fun tr(@StringRes id: Int, vararg args: Any): String =
        appContext.getString(id, *args)

    private fun trPlural(@PluralsRes id: Int, count: Int): String =
        appContext.resources.getQuantityString(id, count, count)

    fun startScan() {
        if (_scanning.value) return
        scanJob?.cancel()
        _devices.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { error ->
                    packetLog.add(LogKind.ERROR, "SCAN", error.message ?: "Scan failed")
                    _scanning.value = false
                }
                .collect { item ->
                    if (_devices.value.none { it.address == item.address }) {
                        packetLog.add(
                            LogKind.SCAN,
                            "ADV",
                            "New device: ${item.name ?: "unnamed"} [${item.address}], rssi=${item.rssi}",
                            item.raw,
                        )
                    }
                    _devices.update { current ->
                        val index = current.indexOfFirst { it.address == item.address }
                        if (index >= 0) {
                            // Update in place: re-sorting by RSSI makes rows jump under the finger
                            // while the user is trying to tap Connect.
                            current.toMutableList().apply { this[index] = item }
                        } else {
                            current + item
                        }
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun setRequestMtuOnConnect(enabled: Boolean) {
        _requestMtuOnConnect.value = enabled
    }

    fun connect(item: ScanItem) {
        stopScan()
        viewModelScope.launch {
            val device = try {
                adapter?.getRemoteDevice(item.address)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (device == null) {
                packetLog.add(LogKind.ERROR, "CONNECT", "Invalid device address ${item.address}")
                _snackbar.tryEmit(tr(R.string.error_invalid_address))
                return@launch
            }

            _connectingAddress.value = item.address
            try {
                if (!client.connect(device)) {
                    _snackbar.tryEmit(tr(R.string.error_connection_failed))
                    return@launch
                }
                _screen.value = LabScreen.Device
                if (_requestMtuOnConnect.value) {
                    client.requestMtu(517)
                }
                if (client.discoverServices()) {
                    client.subscribeAll()
                } else {
                    _snackbar.tryEmit(tr(R.string.error_discovery_failed))
                }
            } finally {
                _connectingAddress.value = null
            }
        }
    }

    fun disconnect() {
        client.disconnect()
        _screen.value = LabScreen.Scan
    }

    fun setNotifications(chUuid: UUID, enabled: Boolean) {
        viewModelScope.launch {
            if (!client.setNotifications(chUuid, enabled)) {
                _snackbar.tryEmit(tr(R.string.error_notifications))
            }
        }
    }

    fun read(chUuid: UUID) {
        viewModelScope.launch {
            if (client.readCharacteristic(chUuid) == null) {
                _snackbar.tryEmit(tr(R.string.error_read_failed))
            }
        }
    }

    fun readAll() {
        viewModelScope.launch {
            val count = client.readAllReadable()
            _snackbar.tryEmit(trPlural(R.plurals.msg_read_count, count))
        }
    }

    fun subscribeAll() {
        viewModelScope.launch {
            val count = client.subscribeAll()
            _snackbar.tryEmit(trPlural(R.plurals.msg_subscribed_count, count))
        }
    }

    fun write(chUuid: UUID, value: ByteArray, withResponse: Boolean) {
        viewModelScope.launch {
            if (!client.writeCharacteristic(chUuid, value, withResponse)) {
                _snackbar.tryEmit(tr(R.string.error_write_failed))
            }
        }
    }

    fun addMarker(text: String) {
        packetLog.marker(text)
    }

    fun clearLog() {
        packetLog.clear()
    }

    fun exportCsv(): String = LogExporter.toCsv(exportMetadata(), entries.value)

    fun exportLogText(): String = LogExporter.toText(entries.value)

    fun exportGattDump(): String = buildGattDump(gattState.value)

    private fun buildGattDump(state: GattState): String {
        return buildString {
            appendLine("# Plan & Measure BLE Lab - GATT dump")
            appendLine("# device: ${state.deviceName ?: "unknown"} [${state.deviceAddress ?: "?"}]")
            appendLine("# mtu: ${state.mtu}")
            appendLine("# android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            for (service in state.services) {
                appendLine("service ${service.uuid}  (${BleUuids.label(service.uuid)})")
                for (characteristic in service.characteristics) {
                    appendLine(
                        "  char ${characteristic.uuid}  [${characteristic.propertiesLabel}]" +
                            "  (${BleUuids.label(characteristic.uuid)})",
                    )
                    if (characteristic.descriptorUuids.isNotEmpty()) {
                        appendLine(
                            "       descriptors: " +
                                characteristic.descriptorUuids.joinToString(", ") { BleUuids.label(it) },
                        )
                    }
                }
            }
        }
    }

    private fun exportMetadata(): List<Pair<String, String>> {
        val state = gattState.value
        val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return listOf(
            "tool" to "Plan & Measure BLE Lab",
            "exported_at" to exportedAt,
            "device_name" to (state.deviceName ?: "unknown"),
            "device_address" to (state.deviceAddress ?: "unknown"),
            "mtu" to state.mtu.toString(),
            "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    override fun onCleared() {
        stopScan()
        client.disconnect()
        super.onCleared()
    }
}
