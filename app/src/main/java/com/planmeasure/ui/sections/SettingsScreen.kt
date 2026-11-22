package com.planmeasure.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.settings.AppLanguage
import com.planmeasure.settings.ProjectTreeStyle
import com.planmeasure.settings.SettingsViewModel
import com.planmeasure.settings.ThemeMode
import com.planmeasure.ui.components.SectionScaffold

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsState()

    SectionScaffold(titleRes = R.string.section_settings, onBack = onBack) {
        SettingsCard(titleRes = R.string.settings_theme) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                SettingChoiceRow(
                    label = stringResource(mode.labelRes()),
                    selected = settings.themeMode == mode,
                    onClick = { settingsViewModel.setThemeMode(mode) },
                )
            }
        }

        SettingsCard(titleRes = R.string.settings_language) {
            AppLanguage.entries.forEachIndexed { index, language ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                SettingChoiceRow(
                    label = stringResource(language.labelRes()),
                    selected = settings.language == language,
                    onClick = {
                        if (settings.language != language) {
                            settingsViewModel.setLanguage(language)
                            onLanguageChanged()
                        }
                    },
                )
            }
        }

        SettingsCard(titleRes = R.string.settings_project_tree) {
            ProjectTreeStyle.entries.forEachIndexed { index, style ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                SettingChoiceRow(
                    label = stringResource(style.labelRes()),
                    selected = settings.projectTreeStyle == style,
                    onClick = { settingsViewModel.setProjectTreeStyle(style) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    @StringRes titleRes: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun SettingChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.English -> R.string.settings_language_english
    AppLanguage.Russian -> R.string.settings_language_russian
}

@StringRes
private fun ProjectTreeStyle.labelRes(): Int = when (this) {
    ProjectTreeStyle.Headers -> R.string.settings_project_tree_headers
    ProjectTreeStyle.Nested -> R.string.settings_project_tree_nested
}
