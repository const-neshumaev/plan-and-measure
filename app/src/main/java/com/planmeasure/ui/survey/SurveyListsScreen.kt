package com.planmeasure.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.survey.SurveyDocument
import com.planmeasure.survey.SurveyViewModel
import com.planmeasure.ui.components.EmptyState
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.defaultNameTimestamp
import com.planmeasure.ui.components.formatUpdatedAt
import java.util.Locale

/** Saved photo sketches; the entry point of the Capture section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyListsScreen(
    viewModel: SurveyViewModel,
    projectObjectIds: Set<String>,
    onDeleteSurvey: (String) -> Unit,
    onConvertToPlan: (SurveyDocument, String) -> Unit,
    onBack: () -> Unit,
) {
    val surveys by viewModel.surveys.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val visibleSurveys = surveys.filter { it.id !in projectObjectIds }
    val snackbarHostState = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val defaultTimestamp = remember(locale) { defaultNameTimestamp(locale) }
    val defaultName = stringResource(R.string.survey_default_name, defaultTimestamp)
    val defaultPlanName = stringResource(R.string.plan_default_name, defaultTimestamp)

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SurveyDocument?>(null) }
    var deleting by remember { mutableStateOf<SurveyDocument?>(null) }
    var converting by remember { mutableStateOf<SurveyDocument?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_capture)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.survey_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            visibleSurveys.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.survey_lists_empty_title),
                    text = stringResource(R.string.survey_lists_empty_text),
                    actionLabel = stringResource(R.string.survey_new),
                    onAction = { showCreate = true },
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleSurveys, key = { it.id }) { survey ->
                    SurveyRow(
                        survey = survey,
                        viewModel = viewModel,
                        locale = locale,
                        onOpen = { viewModel.open(survey.id) },
                        onRename = { renaming = survey },
                        onConvert = { converting = survey },
                        onDelete = { deleting = survey },
                    )
                }
            }
        }
    }

    if (showCreate) {
        ListNameDialog(
            title = stringResource(R.string.survey_new),
            labelRes = R.string.survey_name,
            hintRes = R.string.survey_name_hint,
            initialName = defaultName,
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                showCreate = false
                viewModel.createSurvey(name)
            },
            onDismiss = { showCreate = false },
        )
    }

    renaming?.let { survey ->
        ListNameDialog(
            title = stringResource(R.string.survey_rename_title),
            labelRes = R.string.survey_name,
            hintRes = R.string.survey_name_hint,
            initialName = survey.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                renaming = null
                viewModel.renameSurvey(survey.id, name)
            },
            onDismiss = { renaming = null },
        )
    }

    converting?.let { survey ->
        ListNameDialog(
            title = stringResource(R.string.survey_convert_to_plan),
            labelRes = R.string.plan_name,
            hintRes = R.string.plan_name_hint,
            initialName = defaultPlanName,
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                converting = null
                onConvertToPlan(survey, name)
            },
            onDismiss = { converting = null },
        )
    }

    deleting?.let { survey ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.survey_delete_title)) },
            text = { Text(stringResource(R.string.survey_delete_text, survey.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDeleteSurvey(survey.id)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SurveyRow(
    survey: SurveyDocument,
    viewModel: SurveyViewModel,
    locale: Locale,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onConvert: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurveyThumbnail(
                surveyId = survey.id,
                photo = survey.photo,
                viewModel = viewModel,
                modifier = Modifier.size(64.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = survey.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.survey_meta,
                        pluralStringResource(
                            R.plurals.survey_segments_count,
                            survey.segmentCount,
                            survey.segmentCount,
                        ),
                        stringResource(
                            R.string.survey_updated,
                            formatUpdatedAt(survey.updatedAt, locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.action_more),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_visibility),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    if (survey.hasSketch) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.survey_convert_to_plan)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_section_plans),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onConvert()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
