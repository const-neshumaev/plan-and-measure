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
import com.planmeasure.settings.ProjectTreeStyle
import com.planmeasure.survey.SurveyViewModel
import com.planmeasure.ui.measurements.MeasurementListScreen
import com.planmeasure.ui.plans.PlanEditorScreen
import com.planmeasure.ui.survey.SurveyEditorScreen

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
    treeStyle: ProjectTreeStyle,
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
    viewModel: ProjectsViewModel = viewModel(),
    measurementsViewModel: MeasurementsViewModel = viewModel(),
    plansViewModel: PlansViewModel = viewModel(),
    surveysViewModel: SurveyViewModel = viewModel(),
) {
    val projects by viewModel.projects.collectAsState()
    val openProject by viewModel.openProject.collectAsState()
    val measurementLists by measurementsViewModel.lists.collectAsState()
    val plans by plansViewModel.plans.collectAsState()
    val surveys by surveysViewModel.surveys.collectAsState()
    val openList by measurementsViewModel.openList.collectAsState()
    val openPlan by plansViewModel.openPlan.collectAsState()
    val openSurvey by surveysViewModel.openSurvey.collectAsState()
    val objectKey by viewModel.openObject.collectAsState()

    val objectNames = remember(measurementLists, plans, surveys) {
        buildMap {
            measurementLists.forEach {
                put(ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, it.id), it.name)
            }
            plans.forEach {
                put(ProjectObjectKey(ProjectObjectKind.PLAN, it.id), it.name)
            }
            surveys.forEach {
                put(ProjectObjectKey(ProjectObjectKind.SURVEY, it.id), it.name)
            }
        }
    }

    fun closeObject() {
        viewModel.closeObject()
        measurementsViewModel.close()
        plansViewModel.close()
        surveysViewModel.close()
    }

    fun openObject(ref: ProjectObjectRef) {
        viewModel.selectObject(ref.key())
        when (ref.kind) {
            ProjectObjectKind.MEASUREMENT_LIST -> {
                plansViewModel.close()
                surveysViewModel.close()
                measurementsViewModel.open(ref.refId)
            }

            ProjectObjectKind.PLAN -> {
                measurementsViewModel.close()
                surveysViewModel.close()
                plansViewModel.open(ref.refId)
            }

            ProjectObjectKind.SURVEY -> {
                measurementsViewModel.close()
                plansViewModel.close()
                surveysViewModel.open(ref.refId)
            }
        }
    }

    // Deleting an object from the tree or from its editor removes references to it in every
    // project after the document file is gone (ADR 0017).
    val deleteList: (String) -> Unit = { id ->
        measurementsViewModel.deleteList(id) {
            viewModel.removeReferenceEverywhere(ProjectObjectKind.MEASUREMENT_LIST, id)
        }
    }
    val deletePlan: (String) -> Unit = { id ->
        plansViewModel.deletePlan(id) {
            viewModel.removeReferenceEverywhere(ProjectObjectKind.PLAN, id)
        }
    }
    val deleteSurvey: (String) -> Unit = { id ->
        surveysViewModel.deleteSurvey(id) {
            viewModel.removeReferenceEverywhere(ProjectObjectKind.SURVEY, id)
        }
    }
    val deleteObject: (ProjectObjectRef) -> Unit = { ref ->
        when (ref.kind) {
            ProjectObjectKind.MEASUREMENT_LIST -> deleteList(ref.refId)
            ProjectObjectKind.PLAN -> deletePlan(ref.refId)
            ProjectObjectKind.SURVEY -> deleteSurvey(ref.refId)
        }
    }

    BackHandler(enabled = openProject == null) { onExit() }

    val project = openProject
    val list = openList
    val plan = openPlan
    val survey = openSurvey
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
                onDeleteList = deleteList,
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
                onDeletePlan = deletePlan,
                onBack = ::closeObject,
            )
        }

        currentObject != null &&
            currentObject.kind == ProjectObjectKind.SURVEY &&
            survey != null &&
            survey.id == currentObject.refId -> {
            SurveyEditorScreen(
                survey = survey,
                viewModel = surveysViewModel,
                labViewModel = labViewModel,
                onOpenConnect = onOpenConnect,
                onDeleteSurvey = deleteSurvey,
                onBack = ::closeObject,
            )
        }

        project != null -> {
            ProjectTreeScreen(
                project = project,
                projects = projects,
                measurementLists = measurementLists,
                plans = plans,
                surveys = surveys,
                treeStyle = treeStyle,
                viewModel = viewModel,
                measurementsViewModel = measurementsViewModel,
                plansViewModel = plansViewModel,
                surveysViewModel = surveysViewModel,
                onDeleteObject = deleteObject,
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
