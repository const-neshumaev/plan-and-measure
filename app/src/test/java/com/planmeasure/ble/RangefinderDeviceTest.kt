package com.planmeasure.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangefinderDeviceTest {

    @Test
    fun rangefinderIsDetectedByServiceUuid() {
        val item = scanItem(name = null, serviceUuids = listOf(BleUuids.RANGEFINDER_SERVICE))

        assertTrue(item.looksLikeRangefinder())
    }

    @Test
    fun rangefinderIsDetectedByName() {
        val item = scanItem(name = "Sniper 80", serviceUuids = emptyList())

        assertTrue(item.looksLikeRangefinder())
    }

    @Test
    fun unrelatedDeviceIsNotRangefinder() {
        val item = scanItem(name = "[TV] Samsung", serviceUuids = emptyList())

        assertFalse(item.looksLikeRangefinder())
    }

    private fun scanItem(name: String?, serviceUuids: List<java.util.UUID>) = ScanItem(
        address = "00:00:00:00:00:00",
        name = name,
        rssi = -70,
        serviceUuids = serviceUuids,
        manufacturer = null,
        raw = null,
        connectable = true,
    )
}
