package com.planmeasure.ui.plans

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
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlansViewModel
import com.planmeasure.settings.ObjectScope
import com.planmeasure.ui.components.EmptyState
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.defaultNameTimestamp
import com.planmeasure.ui.components.formatUpdatedAt
import java.util.Locale

/** Saved plans with drawing previews; the entry point of the Plans section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListsScreen(
    viewModel: PlansViewModel,
    projectNames: Map<String, String> = emptyMap(),
    objectScope: ObjectScope = ObjectScope.All,
    onDeletePlan: (String) -> Unit,
    onBack: () -> Unit,
) {
    val plans by viewModel.plans.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val visiblePlans = if (objectScope == ObjectScope.WithoutProject) {
        plans.filter { it.id !in projectNames }
    } else {
        plans
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val defaultTimestamp = remember(locale) { defaultNameTimestamp(locale) }
    val defaultName = stringResource(R.string.plan_default_name, defaultTimestamp)

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlanDocument?>(null) }
    var deleting by remember { mutableStateOf<PlanDocument?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_plans)) },
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
                text = { Text(stringResource(R.string.plan_new)) },
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

            visiblePlans.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.plan_lists_empty_title),
                    text = stringResource(R.string.plan_lists_empty_text),
                    actionLabel = stringResource(R.string.plan_new),
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
                items(visiblePlans, key = { it.id }) { plan ->
                    PlanRow(
                        plan = plan,
                        projectName = projectNames[plan.id],
                        locale = locale,
                        onOpen = { viewModel.open(plan.id) },
                        onRename = { renaming = plan },
                        onDelete = { deleting = plan },
                    )
                }
            }
        }
    }

    if (showCreate) {
        ListNameDialog(
            title = stringResource(R.string.plan_new),
            labelRes = R.string.plan_name,
            hintRes = R.string.plan_name_hint,
            initialName = defaultName,
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                showCreate = false
                viewModel.createPlan(name)
            },
            onDismiss = { showCreate = false },
        )
    }

    renaming?.let { plan ->
        ListNameDialog(
            title = stringResource(R.string.plan_rename_title),
            labelRes = R.string.plan_name,
            hintRes = R.string.plan_name_hint,
            initialName = plan.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                renaming = null
                viewModel.renamePlan(plan.id, name)
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.plan_delete_title)) },
            text = { Text(stringResource(R.string.plan_delete_text, plan.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDeletePlan(plan.id)
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
private fun PlanRow(
    plan: PlanDocument,
    projectName: String?,
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
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlanThumbnail(
                plan = plan,
                modifier = Modifier.size(64.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.plan_meta,
                        pluralStringResource(
                            R.plurals.plan_edges_count,
                            plan.edgeCount,
                            plan.edgeCount,
                        ),
                        stringResource(
                            R.string.plan_updated,
                            formatUpdatedAt(plan.updatedAt, locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (projectName != null) {
                    Text(
                        text = stringResource(R.string.project_in_project, projectName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
