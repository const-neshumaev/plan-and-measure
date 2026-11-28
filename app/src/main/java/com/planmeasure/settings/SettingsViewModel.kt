package com.planmeasure.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = SettingsStorage(app)

    private val _settings = MutableStateFlow(storage.load())
    val settings = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        if (_settings.value.themeMode == mode) return
        storage.saveThemeMode(mode)
        _settings.update { it.copy(themeMode = mode) }
    }

    fun setLanguage(language: AppLanguage) {
        if (_settings.value.language == language) return
        storage.saveLanguage(language)
        _settings.update { it.copy(language = language) }
    }

    fun setProjectTreeStyle(style: ProjectTreeStyle) {
        if (_settings.value.projectTreeStyle == style) return
        storage.saveProjectTreeStyle(style)
        _settings.update { it.copy(projectTreeStyle = style) }
    }

    fun setObjectScope(scope: ObjectScope) {
        if (_settings.value.objectScope == scope) return
        storage.saveObjectScope(scope)
        _settings.update { it.copy(objectScope = scope) }
    }
}
