package com.planmeasure.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlansViewModel
import com.planmeasure.ui.components.ListNameDialog

/** Editor of one plan: the drawing surface, tool switch, undo, grid toggle and document actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    plan: PlanDocument,
    viewModel: PlansViewModel,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val canUndo by viewModel.canUndo.collectAsState()

    var showGrid by rememberSaveable { mutableStateOf(true) }
    var tool by rememberSaveable { mutableStateOf(PlanTool.Draw) }
    var selectedEdgeId by rememberSaveable { mutableStateOf<String?>(null) }
    // The view starts fitted for every plan and is not part of the document, see ADR 0006.
    var viewport by rememberSaveable(plan.id, stateSaver = PlanViewportSaver) {
        mutableStateOf(PlanViewport())
    }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }

    // The selection must not outlive its edge: undo or a full clear removes it.
    val selectedEdge = plan.edges.firstOrNull { it.id == selectedEdgeId }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = plan.name,
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
                    IconButton(
                        onClick = viewModel::undo,
                        enabled = canUndo,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_undo),
                            contentDescription = stringResource(R.string.action_undo),
                        )
                    }
                    if (selectedEdge != null) {
                        IconButton(
                            onClick = {
                                selectedEdgeId = null
                                viewModel.removeEdge(selectedEdge.id)
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.plan_delete_edge),
                            )
                        }
                    }
                    IconToggleButton(
                        checked = showGrid,
                        onCheckedChange = { showGrid = it },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_grid),
                            contentDescription = stringResource(R.string.plan_grid),
                            tint = if (showGrid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (!viewport.isFitted) {
                        IconButton(onClick = { viewport = PlanViewport() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fit_screen),
                                contentDescription = stringResource(R.string.plan_fit_screen),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.action_more),
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
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plan_clear)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                    )
                                },
                                enabled = plan.hasDrawing,
                                onClick = {
                                    menuOpen = false
                                    showClear = true
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
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // The tools live under the drawing instead of over it: a sheet that runs under the buttons
        // hides its own border, and that border is exactly what the finger has to reach to grow it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PlanCanvas(
                    plan = plan,
                    tool = tool,
                    onSegment = viewModel::addSegment,
                    onMoveNode = viewModel::moveNode,
                    onMoveEdge = viewModel::moveEdge,
                    onSelectEdge = { selectedEdgeId = it },
                    selectedEdgeId = selectedEdge?.id,
                    showGrid = showGrid,
                    viewport = viewport,
                    onViewportChange = { viewport = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!plan.hasDrawing) {
                    Text(
                        text = stringResource(R.string.plan_draw_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 40.dp),
                    )
                }
                // The hint floats over the sheet on purpose: giving it its own row would change
                // the canvas height when the tool changes, and the fitted sheet would jump.
                if (tool == PlanTool.Edit && plan.hasDrawing) {
                    Text(
                        text = stringResource(R.string.plan_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SingleChoiceSegmentedButtonRow {
                    PlanTool.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = tool == value,
                            onClick = {
                                tool = value
                                selectedEdgeId = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = PlanTool.entries.size,
                            ),
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        when (value) {
                                            PlanTool.Draw -> R.drawable.ic_edit
                                            PlanTool.Edit -> R.drawable.ic_select
                                        },
                                    ),
                                    contentDescription = null,
                                )
                            },
                        ) {
                            Text(
                                stringResource(
                                    when (value) {
                                        PlanTool.Draw -> R.string.plan_tool_draw
                                        PlanTool.Edit -> R.string.plan_tool_edit
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        ListNameDialog(
            title = stringResource(R.string.plan_rename_title),
            labelRes = R.string.plan_name,
            hintRes = R.string.plan_name_hint,
            initialName = plan.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                showRename = false
                viewModel.renameOpenPlan(name)
            },
            onDismiss = { showRename = false },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.plan_clear_title)) },
            text = { Text(stringResource(R.string.plan_clear_text, plan.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClear = false
                        viewModel.clearDrawing()
                    },
                ) {
                    Text(stringResource(R.string.plan_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.plan_delete_title)) },
            text = { Text(stringResource(R.string.plan_delete_text, plan.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.deleteOpenPlan()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
