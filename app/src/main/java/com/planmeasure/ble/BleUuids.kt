package com.planmeasure.ble

import java.util.UUID

/**
 * UUID helpers and a small dictionary of well-known BLE identifiers.
 *
 * The dictionary exists to make the GATT tree readable: it is much easier to spot a
 * UART-like service when characteristics are labelled instead of being a wall of hex.
 */
object BleUuids {

    private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** Client Characteristic Configuration Descriptor (0x2902). */
    val CCCD: UUID = UUID.fromString("00002902$BASE_UUID_SUFFIX")

    /** Custom service advertised by the rangefinder (see docs/protocol-research/protocol.md). */
    val RANGEFINDER_SERVICE: UUID = UUID.fromString("0000cbbb$BASE_UUID_SUFFIX")

    private val known: Map<String, String> = buildMap {
        putUuid("00001800$BASE_UUID_SUFFIX", "Generic Access")
        putUuid("00001801$BASE_UUID_SUFFIX", "Generic Attribute")
        putUuid("0000180A$BASE_UUID_SUFFIX", "Device Information")
        putUuid("0000180F$BASE_UUID_SUFFIX", "Battery Service")
        putUuid("00001805$BASE_UUID_SUFFIX", "Current Time Service")
        putUuid("00002A00$BASE_UUID_SUFFIX", "Device Name")
        putUuid("00002A01$BASE_UUID_SUFFIX", "Appearance")
        putUuid("00002A04$BASE_UUID_SUFFIX", "Preferred Connection Parameters")
        putUuid("00002A05$BASE_UUID_SUFFIX", "Service Changed")
        putUuid("00002A19$BASE_UUID_SUFFIX", "Battery Level")
        putUuid("00002A29$BASE_UUID_SUFFIX", "Manufacturer Name")
        putUuid("00002A24$BASE_UUID_SUFFIX", "Model Number")
        putUuid("00002A25$BASE_UUID_SUFFIX", "Serial Number")
        putUuid("00002A26$BASE_UUID_SUFFIX", "Firmware Revision")
        putUuid("00002A27$BASE_UUID_SUFFIX", "Hardware Revision")
        putUuid("00002A28$BASE_UUID_SUFFIX", "Software Revision")
        putUuid("00002A23$BASE_UUID_SUFFIX", "System ID")

        // Nordic UART Service: the most common "transparent serial" profile in cheap BLE modules.
        put("6e400001-b5a3-f393-e0a9-e50e24dcca9e", "Nordic UART Service")
        put("6e400002-b5a3-f393-e0a9-e50e24dcca9e", "NUS RX (write)")
        put("6e400003-b5a3-f393-e0a9-e50e24dcca9e", "NUS TX (notify)")

        // Very common vendor UART-ish services on Chinese BLE modules.
        put("0000ffe0$BASE_UUID_SUFFIX", "Vendor UART service 0xFFE0")
        put("0000ffe1$BASE_UUID_SUFFIX", "Vendor UART characteristic 0xFFE1")
        put("0000fff0$BASE_UUID_SUFFIX", "Vendor UART service 0xFFF0")
        put("0000fff1$BASE_UUID_SUFFIX", "Vendor UART characteristic 0xFFF1")
        put("0000fff2$BASE_UUID_SUFFIX", "Vendor UART characteristic 0xFFF2")
    }

    private fun MutableMap<String, String>.putUuid(uuid: String, name: String) {
        put(uuid.lowercase(), name)
    }

    /** Returns the 16-bit form (`0xFFE1`) for Bluetooth base UUIDs, or `null` for custom UUIDs. */
    fun shortId(uuid: UUID): String? {
        val text = uuid.toString().lowercase()
        if (!text.endsWith(BASE_UUID_SUFFIX)) return null
        return "0x" + text.substring(4, 8).uppercase()
    }

    /** Human readable label: known name, then 16-bit id, then the full UUID. */
    fun label(uuid: UUID): String {
        val text = uuid.toString().lowercase()
        known[text]?.let { return it }
        shortId(uuid)?.let { return it }
        return text
    }
}
