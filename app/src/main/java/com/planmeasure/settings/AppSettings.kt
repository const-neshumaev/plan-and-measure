package com.planmeasure.settings

enum class ThemeMode {
    Light,
    Dark,
}

enum class AppLanguage {
    English,
    Russian,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Light,
    val language: AppLanguage = AppLanguage.English,
)
