package com.planmeasure.ble

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/** A device as seen by the scanner. [raw] keeps the raw advertising payload for analysis. */
data class ScanItem(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<UUID>,
    val manufacturer: String?,
    val raw: ByteArray?,
    val connectable: Boolean,
)

/** Immutable snapshot of one characteristic. */
data class GattCharInfo(
    val uuid: UUID,
    val properties: Int,
    val descriptorUuids: List<UUID>,
    val notifying: Boolean = false,
) {
    val canRead: Boolean
        get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

    val canWrite: Boolean
        get() = properties and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ) != 0

    val canWriteWithResponse: Boolean
        get() = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0

    val canWriteWithoutResponse: Boolean
        get() = properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0

    val canSubscribe: Boolean
        get() = properties and (
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE
            ) != 0

    val propertiesLabel: String
        get() = buildList {
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
        }.joinToString(" ")
}

/** Immutable snapshot of one service. */
data class GattServiceInfo(
    val uuid: UUID,
    val characteristics: List<GattCharInfo>,
)

/** Everything the UI needs to know about the current connection. */
data class GattState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val mtu: Int = DEFAULT_MTU,
    val services: List<GattServiceInfo> = emptyList(),
) {
    val allCharacteristics: List<GattCharInfo>
        get() = services.flatMap { it.characteristics }

    val writableCharacteristics: List<GattCharInfo>
        get() = allCharacteristics.filter { it.canWrite }

    companion object {
        const val DEFAULT_MTU = 23
    }
}
