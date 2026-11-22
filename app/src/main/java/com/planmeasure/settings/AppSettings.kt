package com.planmeasure.settings

enum class ThemeMode {
    Light,
    Dark,
}

enum class AppLanguage {
    English,
    Russian,
}

/** How the project tree renders folders and objects. */
enum class ProjectTreeStyle {
    /** Folder plates as group headers with plain object rows; compact on deep branches. */
    Headers,

    /** Nested cards with a guide line for every ancestor level. */
    Nested,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Light,
    val language: AppLanguage = AppLanguage.English,
    val projectTreeStyle: ProjectTreeStyle = ProjectTreeStyle.Headers,
)
