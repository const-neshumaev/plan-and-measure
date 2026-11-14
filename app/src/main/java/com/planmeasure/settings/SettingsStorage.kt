package com.planmeasure.settings

import android.content.Context
import android.content.res.Configuration

/**
 * Persists user preferences in SharedPreferences.
 *
 * On the first launch the initial values are mirrored from the system (dark mode and supported
 * language, with English as the fallback) and stored, so later system changes do not override
 * the user's explicit choice. Stored values that are no longer valid are treated as missing:
 * this also migrates the legacy "System" preference.
 */
class SettingsStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val storedTheme = readEnumOrNull<ThemeMode>(KEY_THEME_MODE)
        val storedLanguage = readEnumOrNull<AppLanguage>(KEY_LANGUAGE)
        if (storedTheme != null && storedLanguage != null) {
            return AppSettings(themeMode = storedTheme, language = storedLanguage)
        }

        val themeMode = storedTheme ?: detectThemeMode()
        val language = storedLanguage ?: detectLanguage()
        if (storedTheme == null) saveThemeMode(themeMode)
        if (storedLanguage == null) saveLanguage(language)
        return AppSettings(themeMode = themeMode, language = language)
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    private fun detectThemeMode(): ThemeMode {
        val nightMode = appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return themeModeForNightMode(nightMode == Configuration.UI_MODE_NIGHT_YES)
    }

    private fun detectLanguage(): AppLanguage =
        languageForSystemTag(appContext.resources.configuration.locales[0].language)

    private inline fun <reified T : Enum<T>> readEnumOrNull(key: String): T? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { enumValueOf<T>(stored) }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LANGUAGE = "language"
    }
}
