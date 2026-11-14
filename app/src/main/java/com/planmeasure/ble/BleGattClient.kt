package com.planmeasure.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.planmeasure.logging.LogKind
import com.planmeasure.logging.PacketLog
import com.planmeasure.protocol.DistanceUnit
import com.planmeasure.protocol.FrameCodec
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Minimal BLE GATT client built for protocol research.
 *
 * Design rules:
 * - Android allows only one outstanding GATT operation per connection, so every
 *   operation goes through [opMutex] and waits for its callback.
 * - Every request, response, notification and error is written to [PacketLog].
 * - The callback object is created per connection, which makes late callbacks from a
 *   previous connection easy to ignore.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class BleGattClient(
    private val context: Context,
    private val log: PacketLog,
) {
    private val opMutex = Mutex()
    private val _state = MutableStateFlow(GattState())
    val state: StateFlow<GattState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var opDeferred: CompletableDeferred<Any?>? = null
    private var activeOp: GattOp = GattOp.NONE
    private var activeCallback: BluetoothGattCallback? = null

    suspend fun connect(device: BluetoothDevice): Boolean {
        closeCurrent()

        val name = BleUtils.safeName(device)
        log.add(LogKind.SYSTEM, "CONNECT", "Connecting to ${name ?: "unknown"} [${device.address}]")

        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        val callback = createCallback()
        activeCallback = callback

        val newGatt = try {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            log.add(LogKind.ERROR, "CONNECT", "Missing BLUETOOTH_CONNECT permission")
            null
        }

        if (newGatt == null) {
            log.add(LogKind.ERROR, "CONNECT", "connectGatt() returned null")
            if (activeCallback === callback) activeCallback = null
            connectDeferred = null
            return false
        }

        gatt = newGatt
        val connected = try {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            log.add(LogKind.ERROR, "CONNECT", "Connection error: ${e.message}")
            null
        } ?: false

        if (!connected) {
            log.add(LogKind.ERROR, "CONNECT", "Connection failed or timed out")
            closeCurrent()
            return false
        }

        _state.update {
            it.copy(
                connected = true,
                deviceName = name,
                deviceAddress = device.address,
            )
        }
        log.add(LogKind.GATT, "CONNECT", "Connected to ${device.address}")
        return true
    }

    fun disconnect() {
        log.add(LogKind.SYSTEM, "CONNECT", "Disconnect requested")
        closeCurrent()
    }

    suspend fun requestMtu(mtu: Int = 517): Int? {
        val current = gatt ?: return null
        val result = runOp(GattOp.MTU, timeoutMs = 5_000L) { current.requestMtu(mtu) }
        return result as? Int
    }

    suspend fun discoverServices(): Boolean {
        val current = gatt ?: return false
        val result = runOp(GattOp.SERVICES) { current.discoverServices() }
        return result as? Boolean ?: false
    }

    suspend fun readCharacteristic(chUuid: UUID): ByteArray? {
        val current = gatt ?: return null
        val characteristic = findCharacteristic(chUuid) ?: run {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Characteristic not found")
            return null
        }
        val result = runOp(GattOp.READ) { current.readCharacteristic(characteristic) }
        return result as? ByteArray
    }

    suspend fun writeCharacteristic(
        chUuid: UUID,
        value: ByteArray,
        withResponse: Boolean,
    ): Boolean {
        val current = gatt ?: return false
        val characteristic = findCharacteristic(chUuid) ?: run {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Characteristic not found")
            return false
        }
        val writeType = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val supported = if (withResponse) {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        } else {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        }
        if (!supported) {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Write type is not supported")
            return false
        }

        log.add(
            LogKind.WRITE,
            BleUuids.label(chUuid),
            "TX ${value.size} byte(s), ${if (withResponse) "with response" else "without response"}",
            value,
        )

        val result = runOp(GattOp.WRITE, awaitCallback = withResponse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                current.writeCharacteristic(characteristic, value, writeType) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = writeType
                characteristic.value = value
                current.writeCharacteristic(characteristic)
            }
        }
        val written = result as? Boolean ?: false
        if (!written) {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Write failed")
        }
        return written
    }

    suspend fun setNotifications(chUuid: UUID, enabled: Boolean): Boolean {
        val current = gatt ?: return false
        val characteristic = findCharacteristic(chUuid) ?: run {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Characteristic not found")
            return false
        }
        val supportsNotify = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val supportsIndicate = characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (!supportsNotify && !supportsIndicate) {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Notifications are not supported")
            return false
        }

        val localEnabled = try {
            current.setCharacteristicNotification(characteristic, enabled)
        } catch (_: SecurityException) {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Missing BLUETOOTH_CONNECT permission")
            false
        }
        if (!localEnabled) {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "setCharacteristicNotification() failed")
            return false
        }

        val cccd = characteristic.getDescriptor(BleUuids.CCCD) ?: run {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "CCCD (0x2902) not found")
            return false
        }
        val value = when {
            !enabled -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            supportsNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        val result = runOp(GattOp.DESCRIPTOR_WRITE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                current.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            } else {
                cccd.value = value
                current.writeDescriptor(cccd)
            }
        }
        val written = result as? Boolean ?: false
        if (written) {
            setNotifying(chUuid, enabled)
            log.add(
                LogKind.GATT,
                BleUuids.label(chUuid),
                if (enabled) "Notifications enabled" else "Notifications disabled",
            )
        } else {
            log.add(LogKind.ERROR, BleUuids.label(chUuid), "Failed to write CCCD")
        }
        return written
    }

    suspend fun subscribeAll(): Int {
        val candidates = _state.value.allCharacteristics.filter { it.canSubscribe }
        var success = 0
        for (characteristic in candidates) {
            if (setNotifications(characteristic.uuid, true)) success++
        }
        log.add(LogKind.SYSTEM, "GATT", "Subscribed to $success of ${candidates.size} characteristic(s)")
        return success
    }

    suspend fun readAllReadable(): Int {
        val candidates = _state.value.allCharacteristics.filter { it.canRead }
        var success = 0
        for (characteristic in candidates) {
            if (readCharacteristic(characteristic.uuid) != null) success++
        }
        log.add(LogKind.SYSTEM, "GATT", "Read $success of ${candidates.size} characteristic(s)")
        return success
    }

    private suspend fun runOp(
        op: GattOp,
        timeoutMs: Long = OP_TIMEOUT_MS,
        awaitCallback: Boolean = true,
        start: () -> Boolean,
    ): Any? = opMutex.withLock {
        if (gatt == null) {
            log.add(LogKind.ERROR, "GATT", "Not connected")
            return@withLock null
        }
        val deferred = CompletableDeferred<Any?>()
        opDeferred = deferred
        activeOp = op
        try {
            val accepted = try {
                start()
            } catch (_: SecurityException) {
                log.add(LogKind.ERROR, "GATT", "Missing BLUETOOTH_CONNECT permission")
                false
            }
            if (!accepted) {
                log.add(LogKind.ERROR, "GATT", "Stack rejected $op request")
                return@withLock null
            }
            // Write-without-response is considered successful once the stack accepts it:
            // there is no acknowledgement on the air, and waiting for the callback can
            // produce false timeouts on some stacks.
            if (!awaitCallback) return@withLock true
            try {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } catch (e: Exception) {
                log.add(LogKind.ERROR, "GATT", "$op failed: ${e.message}")
                null
            }
        } finally {
            activeOp = GattOp.NONE
            opDeferred = null
        }
    }

    /** Completes the awaited operation only when the callback matches the operation in flight. */
    private fun completeOp(op: GattOp, value: Any?) {
        if (activeOp == op) opDeferred?.complete(value)
    }

    private fun createCallback(): BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            g: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            if (!isActive(this)) return
            log.add(
                if (status == BluetoothGatt.GATT_SUCCESS) LogKind.GATT else LogKind.ERROR,
                "CONNECTION",
                "${profileStateName(newState)}, status=$status (${gattStatusText(status)})",
            )
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.update { it.copy(connected = true) }
                    connectDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectDeferred?.complete(false)
                    connectDeferred = null
                    opDeferred?.completeExceptionally(IllegalStateException("GATT disconnected"))
                    opDeferred = null
                    activeOp = GattOp.NONE
                    try {
                        g.close()
                    } catch (_: SecurityException) {
                        // Nothing to clean up without the permission anyway.
                    }
                    if (gatt === g) gatt = null
                    activeCallback = null
                    _state.value = GattState()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (!isActive(this)) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            log.add(
                if (ok) LogKind.GATT else LogKind.ERROR,
                "DISCOVERY",
                "Services discovered, status=$status",
            )
            if (ok) refreshServices(g)
            completeOp(GattOp.SERVICES, ok)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (!isActive(this)) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            log.add(
                if (ok) LogKind.GATT else LogKind.ERROR,
                "MTU",
                "MTU=$mtu, status=$status",
            )
            if (ok) _state.update { it.copy(mtu = mtu) }
            completeOp(GattOp.MTU, if (ok) mtu else null)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (!isActive(this)) return
            handleRead(characteristic, characteristic.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (!isActive(this)) return
            handleRead(characteristic, value, status)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (!isActive(this)) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            if (!ok) {
                log.add(
                    LogKind.ERROR,
                    BleUuids.label(characteristic.uuid),
                    "Write callback, status=$status",
                )
            }
            completeOp(GattOp.WRITE, ok)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (!isActive(this)) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            log.add(
                if (ok) LogKind.GATT else LogKind.ERROR,
                "CCCD",
                "Descriptor for ${BleUuids.label(descriptor.characteristic.uuid)} written, status=$status",
            )
            completeOp(GattOp.DESCRIPTOR_WRITE, ok)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (!isActive(this)) return
            handleNotification(characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (!isActive(this)) return
            handleNotification(characteristic, value)
        }
    }

    private fun handleRead(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        val ok = status == BluetoothGatt.GATT_SUCCESS
        log.add(
            if (ok) LogKind.READ else LogKind.ERROR,
            BleUuids.label(characteristic.uuid),
            if (ok) "RX ${value.size} byte(s)" else "Read failed, status=$status",
            if (ok) value else null,
        )
        completeOp(GattOp.READ, if (ok) value else null)
    }

    private fun handleNotification(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        val frame = FrameCodec.decode(value)
        val message = if (frame != null) {
            val shown = if (frame.unit == DistanceUnit.FEET) {
                "=> %.3f %s (raw %s)".format(
                    Locale.US,
                    frame.normalizedValue,
                    frame.unitText,
                    frame.valueText,
                )
            } else {
                "=> ${frame.valueText} ${frame.unitText}"
            }
            "RX ${value.size} byte(s) $shown"
        } else {
            "RX ${value.size} byte(s)"
        }
        log.add(
            LogKind.NOTIFY,
            BleUuids.label(characteristic.uuid),
            message,
            value,
        )
    }

    private fun refreshServices(g: BluetoothGatt) {
        val services = g.services.orEmpty().map { service ->
            GattServiceInfo(
                uuid = service.uuid,
                characteristics = service.characteristics.orEmpty().map { characteristic ->
                    GattCharInfo(
                        uuid = characteristic.uuid,
                        properties = characteristic.properties,
                        descriptorUuids = characteristic.descriptors.orEmpty().map { it.uuid },
                    )
                },
            )
        }
        _state.update { it.copy(services = services) }
        val characteristicCount = services.sumOf { it.characteristics.size }
        log.add(
            LogKind.GATT,
            "DISCOVERY",
            "${services.size} service(s), $characteristicCount characteristic(s)",
        )
    }

    private fun setNotifying(chUuid: UUID, enabled: Boolean) {
        _state.update { current ->
            current.copy(
                services = current.services.map { service ->
                    service.copy(
                        characteristics = service.characteristics.map { characteristic ->
                            if (characteristic.uuid == chUuid) {
                                characteristic.copy(notifying = enabled)
                            } else {
                                characteristic
                            }
                        },
                    )
                },
            )
        }
    }

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        val current = gatt ?: return null
        for (service in current.services.orEmpty()) {
            for (characteristic in service.characteristics.orEmpty()) {
                if (characteristic.uuid == uuid) return characteristic
            }
        }
        return null
    }

    private fun closeCurrent() {
        val current = gatt
        gatt = null
        connectDeferred?.complete(false)
        connectDeferred = null
        opDeferred?.completeExceptionally(IllegalStateException("GATT closed"))
        opDeferred = null
        activeOp = GattOp.NONE
        activeCallback = null
        if (current != null) {
            try {
                current.disconnect()
            } catch (_: SecurityException) {
                // Ignore: close() below still releases the client.
            }
            try {
                current.close()
            } catch (_: SecurityException) {
                // Ignore.
            }
        }
        _state.value = GattState()
    }

    private fun isActive(callback: BluetoothGattCallback): Boolean = activeCallback === callback

    private fun profileStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
        BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
        else -> "STATE_UNKNOWN($state)"
    }

    private fun gattStatusText(status: Int): String = when (status) {
        BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
        133 -> "GATT_ERROR"
        8 -> "GATT_CONN_TIMEOUT"
        19 -> "GATT_CONN_TERMINATE_PEER_USER"
        22 -> "GATT_CONN_TERMINATE_LOCAL_HOST"
        else -> "0x%02X".format(status)
    }

    private enum class GattOp {
        NONE,
        SERVICES,
        MTU,
        READ,
        WRITE,
        DESCRIPTOR_WRITE,
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val OP_TIMEOUT_MS = 10_000L
    }
}
