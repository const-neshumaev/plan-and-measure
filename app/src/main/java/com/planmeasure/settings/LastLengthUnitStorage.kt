package com.planmeasure.settings

import android.content.Context
import com.planmeasure.units.LengthUnit

/**
 * Last unit the user picked for a document.
 *
 * New measurement lists and plans start with it, so the usual case needs no unit change. This is a
 * convenience rather than a setting: it is not shown on the settings screen.
 */
class LastLengthUnitStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): LengthUnit = prefs.getString(KEY_UNIT, null)
        ?.let { stored -> runCatching { LengthUnit.valueOf(stored) }.getOrNull() }
        ?: LengthUnit.METERS

    fun save(unit: LengthUnit) {
        prefs.edit().putString(KEY_UNIT, unit.name).apply()
    }

    private companion object {
        const val FILE_NAME = "last_unit"
        const val KEY_UNIT = "unit"
    }
}
