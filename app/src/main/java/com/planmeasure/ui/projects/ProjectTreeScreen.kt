package com.planmeasure.ui.projects

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.planmeasure.settings.ProjectTreeStyle
import com.planmeasure.ui.components.EmptyState
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.defaultNameTimestamp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The tree of one project: folders, referenced objects, and document actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTreeScreen(
    project: ProjectDocument,
    projects: List<ProjectDocument>,
    measurementLists: List<MeasurementListSummary>,
    plans: List<PlanDocument>,
    treeStyle: ProjectTreeStyle,
    viewModel: ProjectsViewModel,
    measurementsViewModel: MeasurementsViewModel,
    plansViewModel: PlansViewModel,
    onDeleteObject: (ProjectObjectRef) -> Unit,
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
    var deletingObject by remember { mutableStateOf<ProjectObjectRef?>(null) }
    var showPruneMissing by rememberSaveable { mutableStateOf(false) }
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
    val rowRails = remember(entries) { railContinuations(entries.map { it.depth }) }
    // References whose documents are gone for good. A file that exists but cannot be decoded is
    // not listed here: it may still be readable after a temporary failure (ADR 0017).
    val missingKeys = remember(project, objectNames) {
        ProjectTree.missingObjectKeys(project, objectNames.keys) { key ->
            when (key.kind) {
                ProjectObjectKind.MEASUREMENT_LIST ->
                    measurementsViewModel.hasDocument(key.refId)

                ProjectObjectKind.PLAN ->
                    plansViewModel.hasDocument(key.refId)
            }
        }
    }

    val listState = rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val invalidDropMessage = stringResource(R.string.project_drag_invalid)
    val rootZoneHeight = 48.dp
    val rootZoneHeightPx = with(density) { rootZoneHeight.toPx() }
    val scrollEdgePx = with(density) { 72.dp.toPx() }
    val dragMoveThresholdPx = with(density) { 8.dp.toPx() }
    val minScrollPx = with(density) { 3.dp.toPx() }
    val maxScrollPx = with(density) { 14.dp.toPx() }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var dragRow by remember { mutableStateOf<DraggedTreeRow?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragStartPointer by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<TreeDropTarget?>(null) }
    // The root strip only catches a drag that entered it from below. Otherwise a row that starts
    // under the strip would jump to the root after a tiny movement.
    var rootZoneArmed by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<PendingTreeMove?>(null) }
    var suppressClicksUntil by remember { mutableStateOf(0L) }

    fun entryName(entry: ProjectTree.Entry): String = when (entry) {
        is ProjectTree.Entry.FolderEntry -> entry.folder.name
        is ProjectTree.Entry.ObjectEntry -> objectNames[entry.ref.key()] ?: entry.ref.refId
    }

    fun dragHasMoved(): Boolean =
        (dragPointer - dragStartPointer).getDistance() >= dragMoveThresholdPx

    fun updateDropTarget() {
        val row = dragRow
        if (row == null || !dragHasMoved()) {
            dropTarget = null
            return
        }
        if (dragPointer.y > rootZoneHeightPx) {
            rootZoneArmed = true
        } else {
            if (rootZoneArmed) {
                dropTarget = TreeDropTarget(
                    folderId = null,
                    rowIndex = null,
                    validity = treeDropValidity(project, row.entry, null),
                )
            } else {
                dropTarget = null
            }
            return
        }
        val index = rowIndexAt(dragPointer.y, listState.layoutInfo)
        val entry = index?.let { entries.getOrNull(it) }
        if (entry == null || index == row.index) {
            dropTarget = null
            return
        }
        val folderId = destinationFolderIdFor(entry)
        dropTarget = TreeDropTarget(
            folderId = folderId,
            rowIndex = index,
            validity = treeDropValidity(project, row.entry, folderId),
        )
    }

    fun scrollSpeed(fraction: Float): Float =
        minScrollPx + (maxScrollPx - minScrollPx) * fraction.coerceIn(0f, 1f)

    fun autoScrollDelta(): Float {
        val height = viewportSize.height.toFloat()
        if (height <= 0f || dragRow == null || !dragHasMoved()) return 0f
        val y = dragPointer.y
        val topScrollStart = rootZoneHeightPx
        if (y >= topScrollStart && y < topScrollStart + scrollEdgePx) {
            val fraction = 1f - (y - topScrollStart) / scrollEdgePx
            return -scrollSpeed(fraction)
        }
        val bottomScrollStart = height - scrollEdgePx
        if (y >= bottomScrollStart && y <= height) {
            val fraction = 1f - (height - y) / scrollEdgePx
            return scrollSpeed(fraction)
        }
        return 0f
    }

    fun finishDrag(completed: Boolean) {
        val row = dragRow
        val target = dropTarget
        dragRow = null
        dropTarget = null
        rootZoneArmed = false
        suppressClicksUntil = System.currentTimeMillis() + 300
        if (!completed || row == null || target == null) return
        when (target.validity) {
            TreeDropValidity.Valid -> {
                pendingMove = PendingTreeMove(row.entry, row.name, target.folderId)
            }

            TreeDropValidity.Invalid -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                dragScope.launch { snackbarHostState.showSnackbar(invalidDropMessage) }
            }

            TreeDropValidity.NoChange -> Unit
        }
    }

    fun clicksSuppressed(): Boolean =
        dragRow != null || System.currentTimeMillis() < suppressClicksUntil

    LaunchedEffect(dragRow) {
        if (dragRow == null) return@LaunchedEffect
        while (true) {
            val delta = autoScrollDelta()
            if (delta != 0f) {
                listState.scrollBy(delta)
                updateDropTarget()
            }
            withFrameNanos { }
        }
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
                            if (missingKeys.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.project_remove_missing_references,
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        topMenuOpen = false
                                        showPruneMissing = true
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(entries, objectNames, project, treeStyle) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                                ?: return@awaitEachGesture
                            val index = rowIndexAt(longPress.position.y, listState.layoutInfo)
                            val entry = index?.let { entries.getOrNull(it) }
                            if (index == null || entry == null) return@awaitEachGesture

                            longPress.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragRow = DraggedTreeRow(entry, entryName(entry), index)
                            dragPointer = longPress.position
                            dragStartPointer = longPress.position
                            dropTarget = null
                            rootZoneArmed = false
                            var completed = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        completed = true
                                        break
                                    }
                                    dragPointer += change.position - change.previousPosition
                                    change.consume()
                                    updateDropTarget()
                                }
                            } finally {
                                finishDrag(completed)
                            }
                        }
                    },
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = dragRow == null,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 16.dp,
                        end = 20.dp,
                        bottom = 96.dp,
                    ),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry ->
                            when (entry) {
                                is ProjectTree.Entry.FolderEntry -> "folder:${entry.folder.id}"
                                is ProjectTree.Entry.ObjectEntry ->
                                    "object:${entry.ref.kind.name}:${entry.ref.refId}"
                            }
                        },
                    ) { index, entry ->
                        val previous = entries.getOrNull(index - 1)
                        val railContinues = rowRails[index]
                        val topGap = if (treeStyle == ProjectTreeStyle.Nested) {
                            if (index == 0) 0.dp else 8.dp
                        } else {
                            gapBefore(previous, entry)
                        }
                        val highlight = when {
                            dropTarget?.rowIndex != index -> DropHighlight.None
                            dropTarget?.validity == TreeDropValidity.Valid -> DropHighlight.Target
                            dropTarget?.validity == TreeDropValidity.Invalid -> DropHighlight.Invalid
                            else -> DropHighlight.None
                        }
                        val dimmed = dragRow?.index == index
                        when (entry) {
                            is ProjectTree.Entry.FolderEntry -> FolderRow(
                                folder = entry.folder,
                                depth = entry.depth,
                                style = treeStyle,
                                topGap = topGap,
                                railContinues = railContinues,
                                highlight = highlight,
                                dimmed = dimmed,
                                expanded = entry.folder.id !in collapsed,
                                onToggle = {
                                    if (!clicksSuppressed()) toggleFolder(entry.folder.id)
                                },
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
                                    style = treeStyle,
                                    topGap = topGap,
                                    railContinues = railContinues,
                                    highlight = highlight,
                                    dimmed = dimmed,
                                    showDivider = treeStyle == ProjectTreeStyle.Headers &&
                                        previous is ProjectTree.Entry.ObjectEntry &&
                                        previous.ref.folderId == ref.folderId,
                                    onOpen = { if (!clicksSuppressed()) onOpenObject(ref) },
                                    onMove = { movingObject = ref },
                                    onRemove = {
                                        viewModel.removeObject(ref.kind, ref.refId)
                                    },
                                    onDelete = { deletingObject = ref },
                                )
                            }
                        }
                    }
                }

                if (dragRow != null && dragHasMoved()) {
                    val rootTarget = dropTarget?.rowIndex == null &&
                        dropTarget?.validity == TreeDropValidity.Valid
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(rootZoneHeight)
                            .zIndex(1f),
                        color = if (rootTarget) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (rootTarget) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shadowElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.project_drag_root_zone),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                dragRow?.let { row ->
                    val ghostHeightPx = with(density) { 56.dp.toPx() }
                    val ghostMaxWidthPx = with(density) { 240.dp.toPx() }
                    val x = (dragPointer.x - with(density) { 24.dp.toPx() })
                        .coerceIn(
                            0f,
                            (viewportSize.width - ghostMaxWidthPx).coerceAtLeast(0f),
                        )
                    val y = (dragPointer.y - ghostHeightPx / 2f).coerceIn(
                        0f,
                        (viewportSize.height - ghostHeightPx).coerceAtLeast(0f),
                    )
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .widthIn(max = 240.dp)
                            .zIndex(2f),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(iconFor(row.entry)),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = row.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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

    deletingObject?.let { ref ->
        val isList = ref.kind == ProjectObjectKind.MEASUREMENT_LIST
        val name = objectNames[ref.key()] ?: ref.refId
        AlertDialog(
            onDismissRequest = { deletingObject = null },
            title = {
                Text(
                    stringResource(
                        if (isList) {
                            R.string.measurement_list_delete_title
                        } else {
                            R.string.plan_delete_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isList) {
                            R.string.measurement_list_delete_text
                        } else {
                            R.string.plan_delete_text
                        },
                        name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingObject = null
                        onDeleteObject(ref)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingObject = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showPruneMissing && missingKeys.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showPruneMissing = false },
            title = { Text(stringResource(R.string.project_remove_missing_references)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.project_remove_missing_references_text,
                        missingKeys.size,
                        missingKeys.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPruneMissing = false
                        viewModel.removeObjects(missingKeys)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPruneMissing = false }) {
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

    pendingMove?.let { move ->
        val isFolder = move.entry is ProjectTree.Entry.FolderEntry
        val destinationName = move.folderId
            ?.let { id -> project.folders.firstOrNull { it.id == id }?.name }
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = {
                Text(
                    stringResource(
                        if (isFolder) {
                            R.string.project_move_folder_title
                        } else {
                            R.string.project_move_object_title
                        },
                        move.name,
                    ),
                )
            },
            text = {
                Text(
                    if (destinationName == null) {
                        stringResource(R.string.project_drag_to_root)
                    } else {
                        stringResource(R.string.project_drag_to_folder, destinationName)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMove = null
                        expand(move.folderId)
                        when (val entry = move.entry) {
                            is ProjectTree.Entry.FolderEntry ->
                                viewModel.moveFolder(entry.folder.id, move.folderId)

                            is ProjectTree.Entry.ObjectEntry ->
                                viewModel.attachObject(
                                    entry.ref.kind,
                                    entry.ref.refId,
                                    move.folderId,
                                )
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_move))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
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
    style: ProjectTreeStyle,
    topGap: Dp,
    railContinues: List<Boolean>,
    highlight: DropHighlight,
    dimmed: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddFolder: () -> Unit,
    onAddList: () -> Unit,
    onAddPlan: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val nested = style == ProjectTreeStyle.Nested
    val containerColor = when {
        nested -> MaterialTheme.colorScheme.surfaceVariant
        depth == 0 -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    TreeRowFrame(
        style = style,
        depth = depth,
        topGap = topGap,
        railContinues = railContinues,
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (dimmed) 0.35f else 1f),
            shape = if (nested) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = dropBorder(highlight),
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (nested) 12.dp else (12 + depth * 20).dp,
                    top = if (nested) 10.dp else 12.dp,
                    bottom = if (nested) 10.dp else 12.dp,
                    end = 4.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 90f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = folder.name,
                    style = if (nested) {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FolderMenuButton(
                    onAddFolder = onAddFolder,
                    onAddList = onAddList,
                    onAddPlan = onAddPlan,
                    onRename = onRename,
                    onMove = onMove,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun FolderMenuButton(
    onAddFolder: () -> Unit,
    onAddList: () -> Unit,
    onAddPlan: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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

@Composable
private fun ObjectRow(
    ref: ProjectObjectRef,
    name: String,
    meta: String,
    depth: Int,
    style: ProjectTreeStyle,
    topGap: Dp,
    railContinues: List<Boolean>,
    highlight: DropHighlight,
    dimmed: Boolean,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
) {
    val icon = when (ref.kind) {
        ProjectObjectKind.MEASUREMENT_LIST -> R.drawable.ic_section_measurements
        ProjectObjectKind.PLAN -> R.drawable.ic_section_plans
    }
    val nested = style == ProjectTreeStyle.Nested
    val contentStart = if (nested) 12.dp else (12 + depth * 20).dp

    TreeRowFrame(
        style = style,
        depth = depth,
        topGap = topGap,
        railContinues = railContinues,
    ) {
        Column {
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = contentStart + 28.dp, end = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Surface(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (dimmed) 0.35f else 1f),
                shape = if (nested) MaterialTheme.shapes.medium else MaterialTheme.shapes.small,
                color = if (nested) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                } else {
                    Color.Transparent
                },
                contentColor = if (nested) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = dropBorder(highlight),
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = contentStart,
                        top = 10.dp,
                        bottom = 10.dp,
                        end = 4.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Keeps a slot for the folder chevron so object names line up with folder names
                    // of the same depth.
                    Spacer(Modifier.size(20.dp))
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ObjectMenuButton(
                        onMove = onMove,
                        onRemove = onRemove,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectMenuButton(
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
            ProjectMenuItem(
                icon = R.drawable.ic_delete,
                labelRes = R.string.project_delete_object,
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Places one tree row. The nested layout indents rows on the left and draws a guide line for
 * every ancestor level; the header layout keeps full-width rows and only adds the gap. Rows are
 * indented only on the left so all nested cards share the same right edge.
 *
 * [railContinues] tells, for each ancestor level, whether its guide line runs past this row.
 * The deepest rail that stops here turns into an "L" corner into the row instead of hanging below
 * it; a rail that does not continue at all is left out, because its subtree has already ended.
 */
@Composable
private fun TreeRowFrame(
    style: ProjectTreeStyle,
    depth: Int,
    topGap: Dp,
    railContinues: List<Boolean>,
    content: @Composable () -> Unit,
) {
    val nested = style == ProjectTreeStyle.Nested
    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!nested || depth == 0) return@drawBehind
                val stepPx = NESTED_STEP.toPx()
                val strokePx = 1.dp.toPx()
                // The gap belongs to the drawing bounds, so rails stay continuous between rows.
                val centerY = (size.height + topGap.toPx()) / 2f
                for (level in 0 until depth) {
                    val x = level * stepPx + stepPx / 2f
                    val continues = railContinues.getOrElse(level) { false }
                    val isParent = level == depth - 1
                    if (isParent) {
                        val endY = if (continues) size.height else centerY
                        drawLine(railColor, Offset(x, 0f), Offset(x, endY), strokePx)
                    } else if (continues) {
                        drawLine(railColor, Offset(x, 0f), Offset(x, size.height), strokePx)
                    }
                }
                val parentX = (depth - 1) * stepPx + stepPx / 2f
                drawLine(
                    color = railColor,
                    start = Offset(parentX, centerY),
                    end = Offset(depth * stepPx, centerY),
                    strokeWidth = strokePx,
                )
            }
            .padding(top = topGap),
    ) {
        Box(
            modifier = if (nested) {
                Modifier.padding(start = NESTED_STEP * depth)
            } else {
                Modifier
            },
        ) {
            content()
        }
    }
}

@Composable
private fun dropBorder(highlight: DropHighlight): BorderStroke? = when (highlight) {
    DropHighlight.Target -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    DropHighlight.Invalid -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
    DropHighlight.None -> null
}

@DrawableRes
private fun iconFor(entry: ProjectTree.Entry): Int = when (entry) {
    is ProjectTree.Entry.FolderEntry -> R.drawable.ic_folder
    is ProjectTree.Entry.ObjectEntry -> when (entry.ref.kind) {
        ProjectObjectKind.MEASUREMENT_LIST -> R.drawable.ic_section_measurements
        ProjectObjectKind.PLAN -> R.drawable.ic_section_plans
    }
}

private val NESTED_STEP = 16.dp

/**
 * Vertical gap above a row. Objects of one folder are packed into a group under its header,
 * while a new folder or an object from another level starts a new group.
 */
private fun gapBefore(previous: ProjectTree.Entry?, entry: ProjectTree.Entry): Dp {
    if (previous == null) return 0.dp
    if (entry is ProjectTree.Entry.ObjectEntry) {
        val previousObject = previous as? ProjectTree.Entry.ObjectEntry
        if (previousObject != null && previousObject.ref.folderId == entry.ref.folderId) return 0.dp
        val previousFolder = previous as? ProjectTree.Entry.FolderEntry
        if (previousFolder != null && previousFolder.folder.id == entry.ref.folderId) return 2.dp
        return 8.dp
    }
    if (entry is ProjectTree.Entry.FolderEntry) {
        val previousFolder = previous as? ProjectTree.Entry.FolderEntry
        if (previousFolder != null && previousFolder.folder.id == entry.folder.parentId) return 6.dp
        return 12.dp
    }
    return 0.dp
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
