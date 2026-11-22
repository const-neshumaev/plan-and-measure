package com.planmeasure.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.LabViewModel
import com.planmeasure.settings.SettingsViewModel
import com.planmeasure.ui.connect.ConnectScreen
import com.planmeasure.ui.home.HomeScreen
import com.planmeasure.ui.lab.LabFlow
import com.planmeasure.ui.measurements.MeasurementsFlow
import com.planmeasure.ui.plans.PlansFlow
import com.planmeasure.ui.projects.ProjectsFlow
import com.planmeasure.ui.sections.CaptureScreen
import com.planmeasure.ui.sections.SettingsScreen

enum class HomeSection {
    Measurements,
    Plans,
    Projects,
    Capture,
    Settings,
}

/**
 * Top-level navigation between the home menu, the MVP sections and the BLE lab.
 *
 * The lab keeps its own scan/device navigation inside [LabViewModel]; this root only decides
 * which top-level branch is visible.
 */
@Composable
fun AppRoot(
    viewModel: LabViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onLanguageChanged: () -> Unit = {},
) {
    var section by rememberSaveable { mutableStateOf<HomeSection?>(null) }
    var labOpen by rememberSaveable { mutableStateOf(false) }
    var connectOpen by rememberSaveable { mutableStateOf(false) }
    val settings by settingsViewModel.settings.collectAsState()

    BackHandler(
        enabled = section != null &&
            section != HomeSection.Measurements &&
            section != HomeSection.Plans &&
            section != HomeSection.Projects &&
            !labOpen &&
            !connectOpen,
    ) { section = null }

    if (labOpen) {
        LabFlow(viewModel = viewModel, onExit = { labOpen = false })
        return
    }

    if (connectOpen) {
        ConnectScreen(viewModel = viewModel, onDone = { connectOpen = false })
        return
    }

    when (section) {
        null -> HomeScreen(
            viewModel = viewModel,
            onOpenSection = { section = it },
            onOpenConnect = { connectOpen = true },
            onOpenLab = { labOpen = true },
        )

        HomeSection.Measurements -> MeasurementsFlow(
            labViewModel = viewModel,
            onExit = { section = null },
            onOpenConnect = { connectOpen = true },
        )

        HomeSection.Plans -> PlansFlow(
            labViewModel = viewModel,
            onExit = { section = null },
            onOpenConnect = { connectOpen = true },
        )

        HomeSection.Projects -> ProjectsFlow(
            labViewModel = viewModel,
            treeStyle = settings.projectTreeStyle,
            onExit = { section = null },
            onOpenConnect = { connectOpen = true },
        )
        HomeSection.Capture -> CaptureScreen(onBack = { section = null })
        HomeSection.Settings -> SettingsScreen(
            settingsViewModel = settingsViewModel,
            onBack = { section = null },
            onLanguageChanged = onLanguageChanged,
        )
    }
}
