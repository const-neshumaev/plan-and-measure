package com.planmeasure.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.LabViewModel
import com.planmeasure.plans.PlansViewModel
import com.planmeasure.projects.ProjectObjectKind
import com.planmeasure.projects.ProjectTree
import com.planmeasure.projects.ProjectsViewModel
import com.planmeasure.settings.ObjectScope

/**
 * Navigation inside the Plans section: saved plans, then the editor of the open plan.
 *
 * The open plan lives in the ViewModel, so leaving the section temporarily does not lose it.
 */
@Composable
fun PlansFlow(
    labViewModel: LabViewModel,
    objectScope: ObjectScope,
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
    viewModel: PlansViewModel = viewModel(),
    projectsViewModel: ProjectsViewModel = viewModel(),
) {
    val openPlan by viewModel.openPlan.collectAsState()
    val projects by projectsViewModel.projects.collectAsState()
    val projectNames = remember(projects) {
        ProjectTree.projectNames(projects, ProjectObjectKind.PLAN)
    }
    val deletePlan: (String) -> Unit = { id ->
        viewModel.deletePlan(id) {
            projectsViewModel.removeReferenceEverywhere(ProjectObjectKind.PLAN, id)
        }
    }

    BackHandler(enabled = openPlan == null) { onExit() }

    val plan = openPlan
    if (plan == null) {
        PlanListsScreen(
            viewModel = viewModel,
            projectNames = projectNames,
            objectScope = objectScope,
            onDeletePlan = deletePlan,
            onBack = onExit,
        )
    } else {
        PlanEditorScreen(
            plan = plan,
            viewModel = viewModel,
            labViewModel = labViewModel,
            onOpenConnect = onOpenConnect,
            onDeletePlan = deletePlan,
            onBack = viewModel::close,
        )
    }
}
