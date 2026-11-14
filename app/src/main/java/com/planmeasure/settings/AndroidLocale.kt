package com.planmeasure.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Wraps the base context with the app locale so string resources resolve correctly. */
fun Context.withAppLocale(): Context {
    val language = SettingsStorage(this).load().language
    val locale = language.resolveLocale()

    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(configuration)
}
