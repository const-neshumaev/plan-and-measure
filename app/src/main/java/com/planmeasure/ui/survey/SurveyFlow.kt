package com.planmeasure.ui.survey

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmeasure.LabViewModel
import com.planmeasure.projects.ProjectObjectKind
import com.planmeasure.projects.ProjectTree
import com.planmeasure.projects.ProjectsViewModel
import com.planmeasure.settings.ObjectScope
import com.planmeasure.survey.SurveyViewModel

/**
 * Navigation inside the Capture section: saved photo sketches, then the editor of the open one.
 *
 * The open survey lives in the ViewModel, so leaving the section temporarily does not lose it.
 */
@Composable
fun SurveyFlow(
    labViewModel: LabViewModel,
    objectScope: ObjectScope,
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
    viewModel: SurveyViewModel = viewModel(),
    projectsViewModel: ProjectsViewModel = viewModel(),
) {
    val openSurvey by viewModel.openSurvey.collectAsState()
    val projects by projectsViewModel.projects.collectAsState()
    val projectNames = remember(projects) {
        ProjectTree.projectNames(projects, ProjectObjectKind.SURVEY)
    }
    val deleteSurvey: (String) -> Unit = { id ->
        viewModel.deleteSurvey(id) {
            projectsViewModel.removeReferenceEverywhere(ProjectObjectKind.SURVEY, id)
        }
    }

    BackHandler(enabled = openSurvey == null) { onExit() }

    val survey = openSurvey
    if (survey == null) {
        SurveyListsScreen(
            viewModel = viewModel,
            projectNames = projectNames,
            objectScope = objectScope,
            onDeleteSurvey = deleteSurvey,
            onBack = onExit,
        )
    } else {
        SurveyEditorScreen(
            survey = survey,
            viewModel = viewModel,
            labViewModel = labViewModel,
            onOpenConnect = onOpenConnect,
            onDeleteSurvey = deleteSurvey,
            onBack = viewModel::close,
        )
    }
}
