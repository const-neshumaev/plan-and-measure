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

/** Which saved objects the Measurements and Plans sections list. Defaults to [WithoutProject]. */
enum class ObjectScope {
    /** Every object, including ones that belong to a project. */
    All,

    /** Only objects that no project refers to. */
    WithoutProject,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.Light,
    val language: AppLanguage = AppLanguage.English,
    val projectTreeStyle: ProjectTreeStyle = ProjectTreeStyle.Nested,
    val objectScope: ObjectScope = ObjectScope.WithoutProject,
)
