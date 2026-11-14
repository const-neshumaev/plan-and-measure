package com.planmeasure.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

object BleUtils {

    /** Reads the device name without crashing when BLUETOOTH_CONNECT is missing. */
    fun safeName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    }

    /**
     * Returns whether the local adapter is enabled.
     *
     * `BluetoothAdapter.isEnabled` requires BLUETOOTH_CONNECT on Android 12+. When the
     * permission is missing we report `true` and let the permission banner drive the UI,
     * instead of showing a misleading "Bluetooth is off" state.
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return false
        return try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            true
        }
    }
}
