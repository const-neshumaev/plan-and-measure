package com.planmeasure

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planmeasure.ble.BleGattClient
import com.planmeasure.ble.BlePermissions
import com.planmeasure.ble.BleScanner
import com.planmeasure.ble.BleUtils
import com.planmeasure.ble.BleUuids
import com.planmeasure.ble.GattState
import com.planmeasure.ble.LastDeviceStorage
import com.planmeasure.ble.RememberedDevice
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val lastDeviceStorage = LastDeviceStorage(app)

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

    /** The device that will be restored when the connection is lost or the app restarts. */
    private val _lastDevice = MutableStateFlow(lastDeviceStorage.load())
    val lastDevice = _lastDevice.asStateFlow()

    /** True while the app is trying to reconnect to [lastDevice] without user interaction. */
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting = _reconnecting.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar = _snackbar.asSharedFlow()

    private var scanJob: Job? = null
    private var reconnectJob: Job? = null

    /** Set by [disconnect]: a lost link is expected then and must not be restored. */
    private var explicitDisconnect = false

    /** True while [openSession] owns the GATT client, so its internal cleanup is not a link loss. */
    private var sessionInProgress = false

    private val sessionMutex = Mutex()

    init {
        packetLog.add(LogKind.SYSTEM, "APP", "Session started; log file: ${logFile.absolutePath}")
        viewModelScope.launch {
            client.state.collect { state ->
                if (state.services.isNotEmpty()) {
                    runCatching { gattFile.writeText(buildGattDump(state)) }
                }
            }
        }
        observeConnection()
        restoreLastDevice()
    }

    val adapter: BluetoothAdapter? get() = scanner.adapter

    private fun tr(@StringRes id: Int, vararg args: Any): String =
        appContext.getString(id, *args)

    private fun trPlural(@PluralsRes id: Int, count: Int): String =
        appContext.resources.getQuantityString(id, count, count)

    /**
     * Watches the link state to notice a connection lost without an explicit disconnect.
     *
     * [BleGattClient.state] resets to an empty [GattState] both on a real loss and when the
     * client closes a connection on purpose, hence the [explicitDisconnect] and
     * [sessionInProgress] guards.
     */
    private fun observeConnection() {
        viewModelScope.launch {
            var wasConnected = false
            client.state.collect { state ->
                if (wasConnected && !state.connected && !explicitDisconnect && !sessionInProgress) {
                    onConnectionLost()
                }
                wasConnected = state.connected
            }
        }
    }

    private fun onConnectionLost() {
        val device = _lastDevice.value
        packetLog.add(
            LogKind.SYSTEM,
            "CONNECT",
            if (device != null) {
                "Connection lost, restoring [${device.address}]"
            } else {
                "Connection lost"
            },
        )
        if (device != null) startReconnect(device)
    }

    private fun restoreLastDevice() {
        val device = _lastDevice.value ?: return
        packetLog.add(LogKind.SYSTEM, "CONNECT", "Restoring last device [${device.address}]")
        startReconnect(device, firstDelayMs = 0L)
    }

    /**
     * Retries [device] with a growing delay until it connects or the attempts are cancelled.
     *
     * Attempts are not time-limited: the phone can stay out of range for a while, and the
     * user can cancel them from the connection screen.
     */
    private fun startReconnect(
        device: RememberedDevice,
        firstDelayMs: Long = FIRST_RECONNECT_DELAY_MS,
    ) {
        if (reconnectJob?.isActive == true) return
        _reconnecting.value = true
        reconnectJob = viewModelScope.launch {
            var attempt = 0
            var firstIteration = true
            var waitingLogged = false
            while (isActive) {
                delay(if (firstIteration) firstDelayMs else reconnectDelayMs(attempt))
                firstIteration = false
                if (!canUseBluetooth()) {
                    // Permissions can be revoked and Bluetooth can be turned off at any
                    // moment; keep waiting instead of giving the restoration up.
                    if (!waitingLogged) {
                        packetLog.add(
                            LogKind.SYSTEM,
                            "CONNECT",
                            "Auto-reconnect is on hold: Bluetooth is off or permissions are missing",
                        )
                        waitingLogged = true
                    }
                    continue
                }
                waitingLogged = false
                attempt++
                packetLog.add(
                    LogKind.SYSTEM,
                    "CONNECT",
                    "Reconnect attempt $attempt to [${device.address}]",
                )
                val restored = openSession(
                    address = device.address,
                    name = device.name,
                    requestMtu = true,
                    notifyErrors = false,
                )
                if (restored) {
                    packetLog.add(
                        LogKind.GATT,
                        "CONNECT",
                        "Connection restored [${device.address}]",
                    )
                    _reconnecting.value = false
                    return@launch
                }
            }
        }
    }

    private fun reconnectDelayMs(attempt: Int): Long =
        RECONNECT_DELAYS_MS[(attempt - 1).coerceIn(0, RECONNECT_DELAYS_MS.lastIndex)]

    private fun canUseBluetooth(): Boolean =
        BlePermissions.hasAll(appContext) && BleUtils.isBluetoothEnabled(appContext)

    fun startScan() {
        if (_scanning.value) return
        scanJob?.cancel()
        _devices.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            // Results arrive many times per second, so they are published in batches: a list
            // that only grows once in a while stays readable while the user scrolls it.
            val pending = LinkedHashMap<String, ScanItem>()
            val publisher = launch {
                while (isActive) {
                    delay(SCAN_PUBLISH_INTERVAL_MS)
                    publishScanResults(pending)
                }
            }
            try {
                scanner.scan()
                    .catch { error ->
                        packetLog.add(LogKind.ERROR, "SCAN", error.message ?: "Scan failed")
                        _scanning.value = false
                    }
                    .collect { item ->
                        val known = pending.containsKey(item.address) ||
                            _devices.value.any { it.address == item.address }
                        if (!known) {
                            packetLog.add(
                                LogKind.SCAN,
                                "ADV",
                                "New device: ${item.name ?: "unnamed"} [${item.address}], rssi=${item.rssi}",
                                item.raw,
                            )
                        }
                        pending[item.address] = item
                    }
            } finally {
                // Results still waiting for the next tick are dropped: a finished scan must not
                // fill the list of a newer one.
                publisher.cancel()
            }
        }
    }

    private fun publishScanResults(pending: MutableMap<String, ScanItem>) {
        if (pending.isEmpty()) return
        val incoming = pending.values.toList()
        pending.clear()
        _devices.update { current ->
            val merged = current.toMutableList()
            for (item in incoming) {
                val index = merged.indexOfFirst { it.address == item.address }
                if (index >= 0) {
                    // Update in place: re-sorting by RSSI makes rows jump under the finger
                    // while the user is trying to tap Connect.
                    merged[index] = item
                } else {
                    merged.add(item)
                }
            }
            merged
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
        cancelReconnect()
        viewModelScope.launch {
            _connectingAddress.value = item.address
            try {
                val connected = openSession(
                    address = item.address,
                    name = item.name,
                    requestMtu = _requestMtuOnConnect.value,
                    notifyErrors = true,
                )
                if (connected) _screen.value = LabScreen.Device
            } finally {
                _connectingAddress.value = null
            }
        }
    }

    /**
     * Connects to [address], configures the session and remembers the device.
     *
     * Runs under [sessionMutex] because a manual connect and a restore attempt must never
     * drive the GATT client at the same time.
     */
    private suspend fun openSession(
        address: String,
        name: String?,
        requestMtu: Boolean,
        notifyErrors: Boolean,
    ): Boolean = sessionMutex.withLock {
        sessionInProgress = true
        try {
            val device = try {
                adapter?.getRemoteDevice(address)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (device == null) {
                packetLog.add(LogKind.ERROR, "CONNECT", "Invalid device address $address")
                if (notifyErrors) _snackbar.tryEmit(tr(R.string.error_invalid_address))
                return@withLock false
            }

            if (!client.connect(device)) {
                if (notifyErrors) _snackbar.tryEmit(tr(R.string.error_connection_failed))
                return@withLock false
            }
            if (requestMtu) client.requestMtu(517)
            if (!client.discoverServices()) {
                if (notifyErrors) _snackbar.tryEmit(tr(R.string.error_discovery_failed))
                return@withLock false
            }
            client.subscribeAll()

            explicitDisconnect = false
            val remembered = RememberedDevice(
                address = address,
                name = client.state.value.deviceName ?: name,
            )
            lastDeviceStorage.save(remembered)
            _lastDevice.value = remembered
            true
        } finally {
            sessionInProgress = false
        }
    }

    fun disconnect() {
        explicitDisconnect = true
        cancelReconnect()
        client.disconnect()
        _screen.value = LabScreen.Scan
    }

    /** Stops automatic restore attempts; the remembered device itself is kept. */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnecting.value = false
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
        cancelReconnect()
        client.disconnect()
        super.onCleared()
    }

    private companion object {
        const val SCAN_PUBLISH_INTERVAL_MS = 2_000L
        const val FIRST_RECONNECT_DELAY_MS = 2_000L
        val RECONNECT_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
