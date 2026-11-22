package com.planmeasure.ui.projects

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.measurements.MeasurementListSummary
import com.planmeasure.measurements.MeasurementsViewModel
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlansViewModel
import com.planmeasure.projects.ProjectDocument
import com.planmeasure.projects.ProjectFolder
import com.planmeasure.projects.ProjectObjectKey
import com.planmeasure.projects.ProjectObjectKind
import com.planmeasure.projects.ProjectObjectRef
import com.planmeasure.projects.ProjectTree
import com.planmeasure.projects.ProjectsViewModel
import com.planmeasure.projects.key
import com.planmeasure.ui.components.EmptyState
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.defaultNameTimestamp

/** The tree of one project: folders, referenced objects, and document actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTreeScreen(
    project: ProjectDocument,
    projects: List<ProjectDocument>,
    measurementLists: List<MeasurementListSummary>,
    plans: List<PlanDocument>,
    viewModel: ProjectsViewModel,
    measurementsViewModel: MeasurementsViewModel,
    plansViewModel: PlansViewModel,
    onBack: () -> Unit,
    onOpenObject: (ProjectObjectRef) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val defaultTimestamp = remember(locale) { defaultNameTimestamp(locale) }
    val defaultFolderName = stringResource(R.string.project_folder_default_name, defaultTimestamp)
    val defaultListName = stringResource(R.string.measurement_list_default_name, defaultTimestamp)
    val defaultPlanName = stringResource(R.string.plan_default_name, defaultTimestamp)

    val collapsedSaver = remember {
        listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        )
    }
    var collapsed by rememberSaveable(project.id, stateSaver = collapsedSaver) {
        mutableStateOf(emptySet<String>())
    }

    var topMenuOpen by remember { mutableStateOf(false) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var showRenameProject by rememberSaveable { mutableStateOf(false) }
    var showDeleteProject by rememberSaveable { mutableStateOf(false) }
    var showCreateFolder by rememberSaveable { mutableStateOf(false) }
    var createFolderParentId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingFolder by remember { mutableStateOf<ProjectFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<ProjectFolder?>(null) }
    var movingFolder by remember { mutableStateOf<ProjectFolder?>(null) }
    var movingObject by remember { mutableStateOf<ProjectObjectRef?>(null) }
    var addObjectKind by rememberSaveable { mutableStateOf<String?>(null) }
    var addObjectFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createObjectKind by rememberSaveable { mutableStateOf<String?>(null) }
    var createObjectFolderId by rememberSaveable { mutableStateOf<String?>(null) }

    val measurementById = remember(measurementLists) { measurementLists.associateBy { it.id } }
    val planById = remember(plans) { plans.associateBy { it.id } }
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
    val entries = remember(project, collapsed, objectNames) {
        ProjectTree.visibleEntries(project, collapsed, objectNames)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    BackHandler { onBack() }

    fun expand(folderId: String?) {
        if (folderId != null) {
            collapsed = collapsed - ProjectTree.folderPath(project, folderId)
        }
    }

    fun toggleFolder(folderId: String) {
        collapsed = if (folderId in collapsed) collapsed - folderId else collapsed + folderId
    }

    fun openAddObject(folderId: String?, kind: ProjectObjectKind) {
        addObjectFolderId = folderId
        addObjectKind = kind.name
    }

    val moveFolderTo: (String?) -> Unit = { target ->
        val folder = movingFolder
        if (folder != null) {
            movingFolder = null
            expand(target)
            viewModel.moveFolder(folder.id, target)
        }
    }
    val moveObjectTo: (String?) -> Unit = { target ->
        val ref = movingObject
        if (ref != null) {
            movingObject = null
            expand(target)
            viewModel.attachObject(ref.kind, ref.refId, target)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { topMenuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(
                            expanded = topMenuOpen,
                            onDismissRequest = { topMenuOpen = false },
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
                                    topMenuOpen = false
                                    showRenameProject = true
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
                                    topMenuOpen = false
                                    showDeleteProject = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { fabMenuOpen = true },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.action_add)) },
                )
                DropdownMenu(
                    expanded = fabMenuOpen,
                    onDismissRequest = { fabMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.project_folder_new)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            fabMenuOpen = false
                            createFolderParentId = null
                            showCreateFolder = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.project_add_list_title)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_section_measurements),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            fabMenuOpen = false
                            openAddObject(null, ProjectObjectKind.MEASUREMENT_LIST)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.project_add_plan_title)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_section_plans),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            fabMenuOpen = false
                            openAddObject(null, ProjectObjectKind.PLAN)
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.project_tree_empty_title),
                    text = stringResource(R.string.project_tree_empty_text),
                    actionLabel = stringResource(R.string.project_folder_new),
                    onAction = {
                        createFolderParentId = null
                        showCreateFolder = true
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = entries,
                    key = { entry ->
                        when (entry) {
                            is ProjectTree.Entry.FolderEntry -> "folder:${entry.folder.id}"
                            is ProjectTree.Entry.ObjectEntry ->
                                "object:${entry.ref.kind.name}:${entry.ref.refId}"
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is ProjectTree.Entry.FolderEntry -> FolderRow(
                            folder = entry.folder,
                            depth = entry.depth,
                            expanded = entry.folder.id !in collapsed,
                            onToggle = { toggleFolder(entry.folder.id) },
                            onAddFolder = {
                                createFolderParentId = entry.folder.id
                                showCreateFolder = true
                            },
                            onAddList = {
                                openAddObject(
                                    entry.folder.id,
                                    ProjectObjectKind.MEASUREMENT_LIST,
                                )
                            },
                            onAddPlan = {
                                openAddObject(entry.folder.id, ProjectObjectKind.PLAN)
                            },
                            onRename = { renamingFolder = entry.folder },
                            onMove = { movingFolder = entry.folder },
                            onDelete = { deletingFolder = entry.folder },
                        )

                        is ProjectTree.Entry.ObjectEntry -> {
                            val ref = entry.ref
                            val name = objectNames[ref.key()] ?: ref.refId
                            val meta = when (ref.kind) {
                                ProjectObjectKind.MEASUREMENT_LIST -> {
                                    val list = measurementById[ref.refId]
                                    if (list == null) {
                                        ""
                                    } else {
                                        pluralStringResource(
                                            R.plurals.measurements_count,
                                            list.measurementCount,
                                            list.measurementCount,
                                        )
                                    }
                                }

                                ProjectObjectKind.PLAN -> {
                                    val plan = planById[ref.refId]
                                    if (plan == null) {
                                        ""
                                    } else {
                                        pluralStringResource(
                                            R.plurals.plan_edges_count,
                                            plan.edgeCount,
                                            plan.edgeCount,
                                        )
                                    }
                                }
                            }
                            ObjectRow(
                                ref = ref,
                                name = name,
                                meta = meta,
                                depth = entry.depth,
                                onOpen = { onOpenObject(ref) },
                                onMove = { movingObject = ref },
                                onRemove = {
                                    viewModel.removeObject(ref.kind, ref.refId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameProject) {
        ListNameDialog(
            title = stringResource(R.string.project_rename_title),
            labelRes = R.string.project_name,
            hintRes = R.string.project_name_hint,
            initialName = project.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                showRenameProject = false
                viewModel.renameProject(project.id, name)
            },
            onDismiss = { showRenameProject = false },
        )
    }

    if (showDeleteProject) {
        AlertDialog(
            onDismissRequest = { showDeleteProject = false },
            title = { Text(stringResource(R.string.project_delete_title)) },
            text = { Text(stringResource(R.string.project_delete_text, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteProject = false
                        viewModel.deleteProject(project.id)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProject = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showCreateFolder) {
        ListNameDialog(
            title = stringResource(R.string.project_folder_new),
            labelRes = R.string.project_folder_name,
            hintRes = R.string.project_folder_name_hint,
            initialName = defaultFolderName,
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                val parentId = createFolderParentId
                showCreateFolder = false
                expand(parentId)
                viewModel.addFolder(parentId, name)
            },
            onDismiss = { showCreateFolder = false },
        )
    }

    renamingFolder?.let { folder ->
        ListNameDialog(
            title = stringResource(R.string.project_folder_rename_title),
            labelRes = R.string.project_folder_name,
            hintRes = R.string.project_folder_name_hint,
            initialName = folder.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                renamingFolder = null
                viewModel.renameFolder(folder.id, name)
            },
            onDismiss = { renamingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.project_folder_delete_title)) },
            text = { Text(stringResource(R.string.project_folder_delete_text, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingFolder = null
                        viewModel.deleteFolder(folder.id)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    movingFolder?.let { folder ->
        MoveTargetDialog(
            title = stringResource(R.string.project_move_folder_title, folder.name),
            choices = ProjectTree.folderChoices(project, excludeSubtreeOf = folder.id),
            currentFolderId = folder.parentId,
            onPick = moveFolderTo,
            onDismiss = { movingFolder = null },
        )
    }

    movingObject?.let { ref ->
        MoveTargetDialog(
            title = stringResource(
                R.string.project_move_object_title,
                objectNames[ref.key()] ?: ref.refId,
            ),
            choices = ProjectTree.folderChoices(project),
            currentFolderId = ref.folderId,
            onPick = moveObjectTo,
            onDismiss = { movingObject = null },
        )
    }

    val addKind = addObjectKind?.let { value ->
        runCatching { ProjectObjectKind.valueOf(value) }.getOrNull()
    }
    if (addKind != null) {
        val available = when (addKind) {
            ProjectObjectKind.MEASUREMENT_LIST -> measurementLists
                .filterNot { list ->
                    ProjectTree.containsObject(
                        project,
                        ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, list.id),
                    )
                }
                .map { list ->
                    val key = ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, list.id)
                    AvailableObject(
                        key = key,
                        name = list.name,
                        count = list.measurementCount,
                        projectName = ProjectTree.projectNameOf(projects, key),
                    )
                }
                .sortedBy { it.name.lowercase() }

            ProjectObjectKind.PLAN -> plans
                .filterNot { plan ->
                    ProjectTree.containsObject(
                        project,
                        ProjectObjectKey(ProjectObjectKind.PLAN, plan.id),
                    )
                }
                .map { plan ->
                    val key = ProjectObjectKey(ProjectObjectKind.PLAN, plan.id)
                    AvailableObject(
                        key = key,
                        name = plan.name,
                        count = plan.edgeCount,
                        projectName = ProjectTree.projectNameOf(projects, key),
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
        AddObjectDialog(
            kind = addKind,
            objects = available,
            onCreateNew = {
                val folderId = addObjectFolderId
                addObjectKind = null
                createObjectFolderId = folderId
                createObjectKind = addKind.name
            },
            onPick = { key ->
                val folderId = addObjectFolderId
                addObjectKind = null
                expand(folderId)
                viewModel.attachObject(key.kind, key.refId, folderId)
            },
            onDismiss = { addObjectKind = null },
        )
    }

    val createKind = createObjectKind?.let { value ->
        runCatching { ProjectObjectKind.valueOf(value) }.getOrNull()
    }
    if (createKind != null) {
        ListNameDialog(
            title = stringResource(
                if (createKind == ProjectObjectKind.MEASUREMENT_LIST) {
                    R.string.project_create_new_list
                } else {
                    R.string.project_create_new_plan
                },
            ),
            labelRes = if (createKind == ProjectObjectKind.MEASUREMENT_LIST) {
                R.string.measurement_list_name
            } else {
                R.string.plan_name
            },
            hintRes = if (createKind == ProjectObjectKind.MEASUREMENT_LIST) {
                R.string.measurement_list_name_hint
            } else {
                R.string.plan_name_hint
            },
            initialName = if (createKind == ProjectObjectKind.MEASUREMENT_LIST) {
                defaultListName
            } else {
                defaultPlanName
            },
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                val folderId = createObjectFolderId
                createObjectKind = null
                expand(folderId)
                when (createKind) {
                    ProjectObjectKind.MEASUREMENT_LIST -> measurementsViewModel.createList(
                        name = name,
                        open = false,
                        onCreated = { id -> viewModel.attachObject(createKind, id, folderId) },
                    )

                    ProjectObjectKind.PLAN -> plansViewModel.createPlan(
                        name = name,
                        open = false,
                        onCreated = { id -> viewModel.attachObject(createKind, id, folderId) },
                    )
                }
            },
            onDismiss = { createObjectKind = null },
        )
    }
}

@Composable
private fun FolderRow(
    folder: ProjectFolder,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddFolder: () -> Unit,
    onAddList: () -> Unit,
    onAddPlan: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                start = (12 + depth * 20).dp,
                top = 10.dp,
                bottom = 10.dp,
                end = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
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
                    ProjectMenuItem(
                        icon = R.drawable.ic_folder,
                        labelRes = R.string.project_add_folder_here,
                        onClick = {
                            menuOpen = false
                            onAddFolder()
                        },
                    )
                    ProjectMenuItem(
                        icon = R.drawable.ic_section_measurements,
                        labelRes = R.string.project_add_list_here,
                        onClick = {
                            menuOpen = false
                            onAddList()
                        },
                    )
                    ProjectMenuItem(
                        icon = R.drawable.ic_section_plans,
                        labelRes = R.string.project_add_plan_here,
                        onClick = {
                            menuOpen = false
                            onAddPlan()
                        },
                    )
                    ProjectMenuItem(
                        icon = R.drawable.ic_edit,
                        labelRes = R.string.action_rename,
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    ProjectMenuItem(
                        icon = R.drawable.ic_move,
                        labelRes = R.string.action_move,
                        onClick = {
                            menuOpen = false
                            onMove()
                        },
                    )
                    ProjectMenuItem(
                        icon = R.drawable.ic_delete,
                        labelRes = R.string.action_delete,
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

@Composable
private fun ObjectRow(
    ref: ProjectObjectRef,
    name: String,
    meta: String,
    depth: Int,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val icon = when (ref.kind) {
        ProjectObjectKind.MEASUREMENT_LIST -> R.drawable.ic_section_measurements
        ProjectObjectKind.PLAN -> R.drawable.ic_section_plans
    }

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                start = (12 + depth * 20).dp,
                top = 10.dp,
                bottom = 10.dp,
                end = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
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
                    ProjectMenuItem(
                        icon = R.drawable.ic_move,
                        labelRes = R.string.action_move,
                        onClick = {
                            menuOpen = false
                            onMove()
                        },
                    )
                    ProjectMenuItem(
                        icon = R.drawable.ic_close,
                        labelRes = R.string.project_remove_object,
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectMenuItem(
    @DrawableRes icon: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun MoveTargetDialog(
    title: String,
    choices: List<ProjectTree.FolderChoice>,
    currentFolderId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                choices.forEach { choice ->
                    val selected = choice.folderId == currentFolderId
                    Surface(
                        onClick = { onPick(choice.folderId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            text = choice.name ?: stringResource(R.string.project_root),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(
                                start = (12 + choice.depth * 16).dp,
                                top = 12.dp,
                                bottom = 12.dp,
                                end = 12.dp,
                            ),
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AddObjectDialog(
    kind: ProjectObjectKind,
    objects: List<AvailableObject>,
    onCreateNew: () -> Unit,
    onPick: (ProjectObjectKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val listKind = kind == ProjectObjectKind.MEASUREMENT_LIST
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (listKind) R.string.project_add_list_title else R.string.project_add_plan_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (listKind) {
                                R.string.project_create_new_list
                            } else {
                                R.string.project_create_new_plan
                            },
                        ),
                    )
                }
                if (objects.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (listKind) R.string.project_no_lists else R.string.project_no_plans,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                objects.forEach { item ->
                    Surface(
                        onClick = { onPick(item.key) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = pluralStringResource(
                                    if (listKind) {
                                        R.plurals.measurements_count
                                    } else {
                                        R.plurals.plan_edges_count
                                    },
                                    item.count,
                                    item.count,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            item.projectName?.let { projectName ->
                                Text(
                                    text = stringResource(R.string.project_in_project, projectName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

private data class AvailableObject(
    val key: ProjectObjectKey,
    val name: String,
    val count: Int,
    val projectName: String?,
)
