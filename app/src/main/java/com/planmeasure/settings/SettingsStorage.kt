package com.planmeasure.settings

import android.content.Context
import android.content.res.Configuration

/**
 * Persists user preferences in SharedPreferences.
 *
 * On the first launch the theme and language are mirrored from the system (dark mode and a
 * supported language, with English as the fallback) and stored, so later system changes do not
 * override the user's explicit choice. The tree layout is a taste preference and starts with
 * [ProjectTreeStyle.Nested]; tree previews start small ([ProjectTreePreview.Small]) and the
 * editors show hints. Sections always hide objects that belong to projects. Stored values that
 * are no longer valid are treated as missing: this also migrates the legacy "System" preference.
 */
class SettingsStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val storedTheme = readEnumOrNull<ThemeMode>(KEY_THEME_MODE)
        val storedLanguage = readEnumOrNull<AppLanguage>(KEY_LANGUAGE)
        val storedTreeStyle = readEnumOrNull<ProjectTreeStyle>(KEY_PROJECT_TREE_STYLE)
        val storedTreePreview = readEnumOrNull<ProjectTreePreview>(KEY_PROJECT_TREE_PREVIEW)

        val themeMode = storedTheme ?: detectThemeMode()
        val language = storedLanguage ?: detectLanguage()
        val treeStyle = storedTreeStyle ?: ProjectTreeStyle.Nested
        val treePreview = storedTreePreview ?: ProjectTreePreview.Small
        val showHints = prefs.getBoolean(KEY_SHOW_HINTS, true)
        if (storedTheme == null) saveThemeMode(themeMode)
        if (storedLanguage == null) saveLanguage(language)
        if (storedTreeStyle == null) saveProjectTreeStyle(treeStyle)
        if (storedTreePreview == null) saveProjectTreePreview(treePreview)
        if (!prefs.contains(KEY_SHOW_HINTS)) saveShowHints(showHints)
        return AppSettings(
            themeMode = themeMode,
            language = language,
            projectTreeStyle = treeStyle,
            projectTreePreview = treePreview,
            showHints = showHints,
        )
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun saveProjectTreeStyle(style: ProjectTreeStyle) {
        prefs.edit().putString(KEY_PROJECT_TREE_STYLE, style.name).apply()
    }

    fun saveProjectTreePreview(preview: ProjectTreePreview) {
        prefs.edit().putString(KEY_PROJECT_TREE_PREVIEW, preview.name).apply()
    }

    fun saveShowHints(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HINTS, show).apply()
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
        const val KEY_PROJECT_TREE_STYLE = "project_tree_style"
        const val KEY_PROJECT_TREE_PREVIEW = "project_tree_preview"
        const val KEY_SHOW_HINTS = "show_hints"
    }
}
