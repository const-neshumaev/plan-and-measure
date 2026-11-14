package com.planmeasure.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.planmeasure.logging.Hex
import com.planmeasure.logging.LogKind
import com.planmeasure.logging.PacketLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class BleException(message: String) : Exception(message)

/**
 * Continuous BLE scan without filters: the goal is to see every peripheral around,
 * including the rangefinder before we know its advertised name.
 */
class BleScanner(
    context: Context,
    private val log: PacketLog,
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val adapter get() = manager?.adapter

    fun scan(): Flow<ScanItem> = callbackFlow {
        val scanner = try {
            adapter?.bluetoothLeScanner
        } catch (_: SecurityException) {
            null
        }
        if (scanner == null) {
            log.add(LogKind.ERROR, "SCAN", "BLE scanner is not available")
            close(BleException("BLE scanner is not available"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val item = try {
                    result.toScanItem()
                } catch (_: SecurityException) {
                    log.add(LogKind.ERROR, "SCAN", "Missing BLUETOOTH_CONNECT permission")
                    null
                }
                if (item != null) trySend(item)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    val item = try {
                        result.toScanItem()
                    } catch (_: SecurityException) {
                        continue
                    }
                    trySend(item)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log.add(LogKind.ERROR, "SCAN", "Scan failed, errorCode=$errorCode")
                close(BleException("Scan failed, errorCode=$errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        try {
            scanner.startScan(null, settings, callback)
            log.add(LogKind.SYSTEM, "SCAN", "Scan started (low latency, no filters)")
        } catch (e: SecurityException) {
            log.add(LogKind.ERROR, "SCAN", "Missing BLUETOOTH_SCAN permission")
            close(e)
            return@callbackFlow
        }

        awaitClose {
            log.add(LogKind.SYSTEM, "SCAN", "Scan stopped")
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission revoked while scanning: nothing else to do.
            }
        }
    }

    private fun ScanResult.toScanItem(): ScanItem {
        val record = scanRecord
        val services: List<UUID> = record?.serviceUuids?.map { it.uuid }.orEmpty()
        val manufacturer = record?.manufacturerSpecificData?.let { data ->
            buildString {
                for (index in 0 until data.size()) {
                    if (index > 0) append("; ")
                    val companyId = data.keyAt(index)
                    val payload = data.valueAt(index)?.let { Hex.encode(it) }.orEmpty()
                    append("0x%04X=%s".format(companyId, payload))
                }
            }
        }
        val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.isConnectable
        } else {
            true
        }
        return ScanItem(
            address = device.address,
            // BluetoothDevice.getName() is often null right after discovery while the
            // complete local name sits in the advertising payload, so prefer the record.
            name = record?.deviceName ?: BleUtils.safeName(device),
            rssi = rssi,
            serviceUuids = services,
            manufacturer = manufacturer?.takeIf { it.isNotEmpty() },
            raw = record?.bytes,
            connectable = isConnectable,
        )
    }
}
