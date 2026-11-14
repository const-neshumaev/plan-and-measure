package com.planmeasure.ble

/**
 * Best-effort match for the rangefinder: it advertises the custom 0xCBBB service, while
 * some units may only expose a recognizable name in the advertising packet.
 */
fun ScanItem.looksLikeRangefinder(): Boolean =
    serviceUuids.contains(BleUuids.RANGEFINDER_SERVICE) ||
        name?.contains("sniper", ignoreCase = true) == true
