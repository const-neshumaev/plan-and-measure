package com.planmeasure

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.settings.SettingsViewModel
import com.planmeasure.settings.ThemeMode
import com.planmeasure.settings.withAppLocale
import com.planmeasure.ui.AppRoot
import com.planmeasure.ui.theme.PlanMeasureTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // System bar icons and the navigation bar scrim must follow the in-app theme,
            // not the system one: the user can override it in settings.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = LIGHT_NAV_BAR_SCRIM,
                        darkScrim = DARK_NAV_BAR_SCRIM,
                    ) { darkTheme },
                )
            }

            PlanMeasureTheme(darkTheme = darkTheme) {
                AppRoot(
                    settingsViewModel = settingsViewModel,
                    onLanguageChanged = { recreate() },
                )
            }
        }
    }

    private companion object {
        val LIGHT_NAV_BAR_SCRIM = 0xE6FFFFFF.toInt()
        val DARK_NAV_BAR_SCRIM = 0x801B1B1B.toInt()
    }
}
