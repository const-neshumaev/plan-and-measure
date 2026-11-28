package com.planmeasure.settings

enum class ThemeMode {
    Light,
    Dark,
}

enum class AppLanguage {
    English,
    Russian,
}

/** How the project tree renders folders and objects. Defaults to [Nested]. */
enum class ProjectTreeStyle {
    /** Folder plates as group headers with plain object rows; compact on deep branches. */
    Headers,

    /** Nested cards with a guide line for every ancestor level. */
    Nested,
}

/** Size of the plan and survey previews on project tree rows. Defaults to [Small]. */
enum class ProjectTreePreview {
    Small,
    Large,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Light,
    val language: AppLanguage = AppLanguage.English,
    val projectTreeStyle: ProjectTreeStyle = ProjectTreeStyle.Nested,
    val projectTreePreview: ProjectTreePreview = ProjectTreePreview.Small,
    /** Whether the editors show instructional hints over the drawing. */
    val showHints: Boolean = true,
    /** Whether the drawing and editing tools show a loupe around the finger. */
    val showMagnifier: Boolean = true,
)
