package com.planmeasure.ui.measurements

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.LabViewModel
import com.planmeasure.measurements.MeasurementsViewModel

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
) {
    val openList by viewModel.openList.collectAsState()

    BackHandler(enabled = openList == null) { onExit() }

    val list = openList
    if (list == null) {
        MeasurementListsScreen(viewModel = viewModel, onBack = onExit)
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
