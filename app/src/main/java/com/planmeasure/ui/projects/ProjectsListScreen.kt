package com.planmeasure.ui.projects

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
import com.planmeasure.projects.ProjectDocument
import com.planmeasure.projects.ProjectObjectKey
import com.planmeasure.projects.ProjectTree
import com.planmeasure.projects.ProjectsViewModel
import com.planmeasure.ui.components.EmptyState
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.defaultNameTimestamp
import com.planmeasure.ui.components.formatUpdatedAt
import java.util.Locale

/** Saved projects; the entry point of the Projects section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreen(
    viewModel: ProjectsViewModel,
    objectNames: Map<ProjectObjectKey, String>,
    onBack: () -> Unit,
) {
    val projects by viewModel.projects.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val defaultTimestamp = remember(locale) { defaultNameTimestamp(locale) }
    val defaultName = stringResource(R.string.project_default_name, defaultTimestamp)

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectDocument?>(null) }
    var deleting by remember { mutableStateOf<ProjectDocument?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_projects)) },
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
                text = { Text(stringResource(R.string.project_new)) },
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

            projects.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.project_lists_empty_title),
                    text = stringResource(R.string.project_lists_empty_text),
                    actionLabel = stringResource(R.string.project_new),
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
                items(projects, key = { it.id }) { project ->
                    ProjectRow(
                        project = project,
                        objectCount = ProjectTree.liveObjectCount(project, objectNames),
                        locale = locale,
                        onOpen = { viewModel.open(project.id) },
                        onRename = { renaming = project },
                        onDelete = { deleting = project },
                    )
                }
            }
        }
    }

    if (showCreate) {
        ListNameDialog(
            title = stringResource(R.string.project_new),
            labelRes = R.string.project_name,
            hintRes = R.string.project_name_hint,
            initialName = defaultName,
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                showCreate = false
                viewModel.createProject(name)
            },
            onDismiss = { showCreate = false },
        )
    }

    renaming?.let { project ->
        ListNameDialog(
            title = stringResource(R.string.project_rename_title),
            labelRes = R.string.project_name,
            hintRes = R.string.project_name_hint,
            initialName = project.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                renaming = null
                viewModel.renameProject(project.id, name)
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.project_delete_title)) },
            text = { Text(stringResource(R.string.project_delete_text, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.deleteProject(project.id)
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
private fun ProjectRow(
    project: ProjectDocument,
    objectCount: Int,
    locale: Locale,
    onOpen: () -> Unit,
    onRename: () -> Unit,
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
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_section_projects),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.project_meta,
                        pluralStringResource(
                            R.plurals.project_folders_count,
                            project.folders.size,
                            project.folders.size,
                        ),
                        pluralStringResource(
                            R.plurals.project_objects_count,
                            objectCount,
                            objectCount,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(
                        R.string.project_updated,
                        formatUpdatedAt(project.updatedAt, locale),
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
