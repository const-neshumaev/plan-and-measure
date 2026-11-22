package com.planmeasure.ui.projects

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.LabViewModel
import com.planmeasure.measurements.MeasurementsViewModel
import com.planmeasure.plans.PlansViewModel
import com.planmeasure.projects.ProjectObjectKey
import com.planmeasure.projects.ProjectObjectKind
import com.planmeasure.projects.ProjectObjectRef
import com.planmeasure.projects.ProjectsViewModel
import com.planmeasure.projects.key
import com.planmeasure.ui.measurements.MeasurementListScreen
import com.planmeasure.ui.plans.PlanEditorScreen

/**
 * Navigation inside the Projects section: saved projects, the project tree, and an object opened
 * from that tree.
 *
 * The list and plan editors are the same screens as in their own sections. Opening an object from
 * a project keeps the project in the ViewModel, so back returns to the tree instead of the flat
 * list.
 */
@Composable
fun ProjectsFlow(
    labViewModel: LabViewModel,
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
    viewModel: ProjectsViewModel = viewModel(),
    measurementsViewModel: MeasurementsViewModel = viewModel(),
    plansViewModel: PlansViewModel = viewModel(),
) {
    val projects by viewModel.projects.collectAsState()
    val openProject by viewModel.openProject.collectAsState()
    val measurementLists by measurementsViewModel.lists.collectAsState()
    val plans by plansViewModel.plans.collectAsState()
    val openList by measurementsViewModel.openList.collectAsState()
    val openPlan by plansViewModel.openPlan.collectAsState()
    val objectKey by viewModel.openObject.collectAsState()

    val objectNames = remember(measurementLists, plans) {
        buildMap {
            measurementLists.forEach {
                put(ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, it.id), it.name)
            }
            plans.forEach {
                put(ProjectObjectKey(ProjectObjectKind.PLAN, it.id), it.name)
            }
        }
    }

    fun closeObject() {
        viewModel.closeObject()
        measurementsViewModel.close()
        plansViewModel.close()
    }

    fun openObject(ref: ProjectObjectRef) {
        viewModel.selectObject(ref.key())
        when (ref.kind) {
            ProjectObjectKind.MEASUREMENT_LIST -> {
                plansViewModel.close()
                measurementsViewModel.open(ref.refId)
            }

            ProjectObjectKind.PLAN -> {
                measurementsViewModel.close()
                plansViewModel.open(ref.refId)
            }
        }
    }

    BackHandler(enabled = openProject == null) { onExit() }

    val project = openProject
    val list = openList
    val plan = openPlan
    val currentObject = objectKey

    when {
        currentObject != null &&
            currentObject.kind == ProjectObjectKind.MEASUREMENT_LIST &&
            list != null &&
            list.id == currentObject.refId -> {
            MeasurementListScreen(
                list = list,
                viewModel = measurementsViewModel,
                labViewModel = labViewModel,
                onOpenConnect = onOpenConnect,
                onBack = ::closeObject,
            )
        }

        currentObject != null &&
            currentObject.kind == ProjectObjectKind.PLAN &&
            plan != null &&
            plan.id == currentObject.refId -> {
            PlanEditorScreen(
                plan = plan,
                viewModel = plansViewModel,
                labViewModel = labViewModel,
                onOpenConnect = onOpenConnect,
                onBack = ::closeObject,
            )
        }

        project != null -> {
            ProjectTreeScreen(
                project = project,
                projects = projects,
                measurementLists = measurementLists,
                plans = plans,
                viewModel = viewModel,
                measurementsViewModel = measurementsViewModel,
                plansViewModel = plansViewModel,
                onBack = {
                    closeObject()
                    viewModel.close()
                },
                onOpenObject = ::openObject,
            )
        }

        else -> {
            ProjectsListScreen(
                viewModel = viewModel,
                objectNames = objectNames,
                onBack = onExit,
            )
        }
    }
}
