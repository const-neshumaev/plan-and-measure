package com.planmeasure.ui.measurements

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.LabViewModel
import com.planmeasure.measurements.MeasurementsViewModel
import com.planmeasure.projects.ProjectObjectKind
import com.planmeasure.projects.ProjectTree
import com.planmeasure.projects.ProjectsViewModel

/**
 * Navigation inside the Measurements section: saved lists, then the editor of the open list.
 *
 * The editor stays open when the user temporarily leaves the section (for example, to connect
 * the rangefinder), because the ViewModel outlives the composition.
 */
@Composable
fun MeasurementsFlow(
    labViewModel: LabViewModel,
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
    viewModel: MeasurementsViewModel = viewModel(),
    projectsViewModel: ProjectsViewModel = viewModel(),
) {
    val openList by viewModel.openList.collectAsState()
    val projects by projectsViewModel.projects.collectAsState()
    val projectNames = remember(projects) {
        ProjectTree.projectNames(projects, ProjectObjectKind.MEASUREMENT_LIST)
    }

    BackHandler(enabled = openList == null) { onExit() }

    val list = openList
    if (list == null) {
        MeasurementListsScreen(
            viewModel = viewModel,
            projectNames = projectNames,
            onBack = onExit,
        )
    } else {
        MeasurementListScreen(
            list = list,
            viewModel = viewModel,
            labViewModel = labViewModel,
            onOpenConnect = onOpenConnect,
            onBack = viewModel::close,
        )
    }
}
