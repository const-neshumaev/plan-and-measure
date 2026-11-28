package com.planmeasure.ui.survey

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
import com.planmeasure.survey.SurveyDocument
import com.planmeasure.survey.SurveyToPlan
import com.planmeasure.survey.SurveyViewModel

/**
 * Navigation inside the Capture section: saved photo sketches, then the editor of the open one.
 *
 * The open survey lives in the ViewModel, so leaving the section temporarily does not lose it.
 */
@Composable
fun SurveyFlow(
    labViewModel: LabViewModel,
    showHints: Boolean,
    showMagnifier: Boolean,
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenPlans: () -> Unit,
    viewModel: SurveyViewModel = viewModel(),
    projectsViewModel: ProjectsViewModel = viewModel(),
    plansViewModel: PlansViewModel = viewModel(),
) {
    val openSurvey by viewModel.openSurvey.collectAsState()
    val projects by projectsViewModel.projects.collectAsState()
    val projectObjectIds = remember(projects) {
        ProjectTree.projectObjectIds(projects, ProjectObjectKind.SURVEY)
    }
    val deleteSurvey: (String) -> Unit = { id ->
        viewModel.deleteSurvey(id) {
            projectsViewModel.removeReferenceEverywhere(ProjectObjectKind.SURVEY, id)
        }
    }

    // A plan made from a survey is a new independent object: the survey section does not own the
    // project tree, so the plan is not attached to a project here (ADR 0023).
    val convertSurvey: (SurveyDocument, String) -> Unit = { survey, name ->
        val content = SurveyToPlan.convert(survey)
        if (content != null) {
            plansViewModel.createPlan(
                name = name,
                unit = survey.unit,
                content = content,
                onCreated = { onOpenPlans() },
            )
        }
    }

    BackHandler(enabled = openSurvey == null) { onExit() }

    val survey = openSurvey
    if (survey == null) {
        SurveyListsScreen(
            viewModel = viewModel,
            projectObjectIds = projectObjectIds,
            onDeleteSurvey = deleteSurvey,
            onConvertToPlan = convertSurvey,
            onBack = onExit,
        )
    } else {
        SurveyEditorScreen(
            survey = survey,
            viewModel = viewModel,
            labViewModel = labViewModel,
            showHints = showHints,
            showMagnifier = showMagnifier,
            onOpenConnect = onOpenConnect,
            onDeleteSurvey = deleteSurvey,
            onConvertToPlan = convertSurvey,
            onBack = viewModel::close,
        )
    }
}
