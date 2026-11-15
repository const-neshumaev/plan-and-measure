package com.planmeasure.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.plans.PlansViewModel

/**
 * Navigation inside the Plans section: saved plans, then the editor of the open plan.
 *
 * The open plan lives in the ViewModel, so leaving the section temporarily does not lose it.
 */
@Composable
fun PlansFlow(
    onExit: () -> Unit,
    viewModel: PlansViewModel = viewModel(),
) {
    val openPlan by viewModel.openPlan.collectAsState()

    BackHandler(enabled = openPlan == null) { onExit() }

    val plan = openPlan
    if (plan == null) {
        PlanListsScreen(viewModel = viewModel, onBack = onExit)
    } else {
        PlanEditorScreen(
            plan = plan,
            viewModel = viewModel,
            onBack = viewModel::close,
        )
    }
}
