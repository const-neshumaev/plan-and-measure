package com.planmeasure.settings

import java.util.Locale

/** Locale used by the app for an explicit language choice. */
fun AppLanguage.resolveLocale(): Locale = when (this) {
    AppLanguage.English -> Locale.ENGLISH
    AppLanguage.Russian -> Locale.forLanguageTag("ru")
}

/**
 * Initial language for the very first launch. Only supported system languages are adopted;
 * everything else falls back to English.
 */
fun languageForSystemTag(languageTag: String): AppLanguage =
    if (languageTag == "ru") AppLanguage.Russian else AppLanguage.English

/** Initial theme for the very first launch, mirrored from the system dark mode. */
fun themeModeForNightMode(isNightMode: Boolean): ThemeMode =
    if (isNightMode) ThemeMode.Dark else ThemeMode.Light
