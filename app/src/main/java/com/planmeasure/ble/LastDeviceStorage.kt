package com.planmeasure.ble

import android.content.Context

/** A device the app has connected to successfully at least once. */
data class RememberedDevice(
    val address: String,
    val name: String?,
)

/**
 * Remembers the last connected device so the session can be restored after the link is lost
 * or the app is restarted.
 *
 * Kept separate from `SettingsStorage`: this is session state, not a user preference.
 */
class LastDeviceStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): RememberedDevice? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        return RememberedDevice(address = address, name = prefs.getString(KEY_NAME, null))
    }

    fun save(device: RememberedDevice) {
        prefs.edit()
            .putString(KEY_ADDRESS, device.address)
            .putString(KEY_NAME, device.name)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "last_device"
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
    }
}
