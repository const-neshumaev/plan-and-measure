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

    fun setProjectTreePreview(preview: ProjectTreePreview) {
        if (_settings.value.projectTreePreview == preview) return
        storage.saveProjectTreePreview(preview)
        _settings.update { it.copy(projectTreePreview = preview) }
    }

    fun setShowHints(show: Boolean) {
        if (_settings.value.showHints == show) return
        storage.saveShowHints(show)
        _settings.update { it.copy(showHints = show) }
    }

    fun setShowMagnifier(show: Boolean) {
        if (_settings.value.showMagnifier == show) return
        storage.saveShowMagnifier(show)
        _settings.update { it.copy(showMagnifier = show) }
    }
}
