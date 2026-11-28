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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.plans.PlanCalculationKind
import com.planmeasure.plans.PlanDimension
import com.planmeasure.plans.PlanDocument
import com.planmeasure.plans.PlanEditing
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanScale
import com.planmeasure.plans.PlansViewModel
import com.planmeasure.ui.components.ConnectBanner
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.UnitBar
import com.planmeasure.ui.components.labelRes
import com.planmeasure.units.Length
import com.planmeasure.units.LengthUnit
import java.math.BigDecimal

/** Editor of one plan: the drawing surface, tool switch, undo and the plan menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    plan: PlanDocument,
    viewModel: PlansViewModel,
    labViewModel: LabViewModel,
    onOpenConnect: () -> Unit,
    onDeletePlan: (String) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val canUndo by viewModel.canUndo.collectAsState()
    val gattState by labViewModel.gattState.collectAsState()
    val focusManager = LocalFocusManager.current

    var showGrid by rememberSaveable { mutableStateOf(true) }
    var tool by rememberSaveable { mutableStateOf(PlanTool.Draw) }
    var selectedEdgeId by rememberSaveable { mutableStateOf<String?>(null) }
    // A selected wall can outlive its card: a rangefinder reading fills the value and closes the
    // card, while the wall stays selected until the user taps elsewhere.
    var cardOpen by rememberSaveable { mutableStateOf(false) }
    var draftValue by rememberSaveable { mutableStateOf("") }
    // The view starts fitted for every plan and is not part of the document, see ADR 0006.
    var viewport by rememberSaveable(plan.id, stateSaver = PlanViewportSaver) {
        mutableStateOf(PlanViewport())
    }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var pendingSplit by remember { mutableStateOf<PendingEdgeSplit?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var calculationsOpen by rememberSaveable { mutableStateOf(false) }
    // The measurement mode is temporary: it replaces the drawing tools only while the user picks
    // what to measure, and its selection is editor state rather than part of the document.
    var calculationMode by rememberSaveable { mutableStateOf<PlanCalculationKind?>(null) }
    var calculationEdgeIds by rememberSaveable(
        plan.id,
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf<Set<String>>(emptySet()) }
    var subtractHoles by rememberSaveable { mutableStateOf(true) }
    var showSaveCalculation by rememberSaveable { mutableStateOf(false) }

    // The selection must not outlive its edge: undo or a full clear removes it.
    val selectedEdge = plan.edges.firstOrNull { it.id == selectedEdgeId }

    // The field follows the document, so an incoming rangefinder reading, an undo or a unit
    // change replaces the shown value; typing cannot trigger this, because the document changes
    // only on commit.
    LaunchedEffect(selectedEdge?.id, selectedEdge?.dimension, plan.unit) {
        draftValue = selectedEdge?.dimension
            ?.let { Length.format(it.valueMm, plan.unit) }
            .orEmpty()
    }

    fun commitDimension() {
        if (tool != PlanTool.Dimensions || !cardOpen) return
        val edgeId = selectedEdgeId ?: return
        viewModel.setEdgeDimension(edgeId, draftValue)
    }

    fun changeUnit(unit: LengthUnit) {
        if (unit == plan.unit) return
        // Commit what is typed before the unit changes, otherwise the draft would be read in the
        // wrong unit and silently become a different length.
        commitDimension()
        viewModel.setUnit(unit)
    }

    fun closeCard() {
        commitDimension()
        cardOpen = false
    }

    fun startCalculation() {
        commitDimension()
        cardOpen = false
        selectedEdgeId = null
        calculationsOpen = false
        calculationMode = PlanCalculationKind.AREA
        calculationEdgeIds = emptySet()
        subtractHoles = true
    }

    fun cancelCalculation() {
        calculationMode = null
        calculationEdgeIds = emptySet()
        showSaveCalculation = false
    }

    val toggleCalculationEdge: (String) -> Unit = { id ->
        calculationEdgeIds = if (id in calculationEdgeIds) {
            calculationEdgeIds - id
        } else {
            calculationEdgeIds + id
        }
    }
    val clearCalculationSelection: () -> Unit = {
        calculationEdgeIds = emptySet()
    }

    // Selecting another wall closes the card and keeps what was typed. Tapping the selected wall
    // again opens the card a reading closed; tapping empty space drops the selection as well.
    val selectEdge: (String?) -> Unit = { id ->
        when {
            id == null -> {
                commitDimension()
                cardOpen = false
                selectedEdgeId = null
            }
            id == selectedEdgeId -> cardOpen = true
            else -> {
                commitDimension()
                selectedEdgeId = id
                cardOpen = true
            }
        }
    }

    // Splitting a measured wall drops its dimension: the wall it described stops existing. The
    // user confirms the loss first; the split itself is one undo step, so undo restores both.
    val splitEdge: (edgeId: String, at: PlanPoint) -> Unit = { id, at ->
        val dimension = plan.edges.firstOrNull { it.id == id }?.dimension
        if (dimension != null && PlanEditing.canSplitEdge(plan, id, at)) {
            pendingSplit = PendingEdgeSplit(id, at, dimension)
        } else {
            viewModel.splitEdge(id, at)
        }
    }

    // A reading goes only to the wall whose card is open: pointing the device at another wall
    // while the card is closed must not silently rewrite a dimension (ADR 0008).
    val lifecycleOwner = LocalLifecycleOwner.current
    val dimensionEdgeId = selectedEdge?.id?.takeIf { tool == PlanTool.Dimensions && cardOpen }
    LaunchedEffect(dimensionEdgeId, lifecycleOwner) {
        val edgeId = dimensionEdgeId ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            labViewModel.deviceFrames.collect { frame ->
                if (viewModel.onRangefinderFrame(edgeId, frame)) {
                    // The reading is the value now, so there is nothing left to type. Hiding the
                    // keyboard and the card leaves the wall free for the next measurement, which
                    // is the common case: the user walks along the walls with the device.
                    focusManager.clearFocus()
                    cardOpen = false
                }
            }
        }
    }

    val density = LocalDensity.current
    val sheetPadding = with(density) { SheetMargin.toPx() }
    val cardAnchor = remember(plan, viewport, canvasSize, selectedEdge?.id) {
        selectedEdge?.let { edge ->
            dimensionCardAnchor(canvasSize, plan, edge.id, viewport, sheetPadding)
        }
    }

    // How far the drawing is from the saved dimension, shown only when the two disagree. It is
    // derived from the document, never stored (ADR 0012).
    val unitLabel = stringResource(plan.unit.labelRes())
    fun formatLength(millimeters: Double): String {
        val text = Length.format(Length.canonical(BigDecimal.valueOf(millimeters)), plan.unit)
        return "$text $unitLabel"
    }

    val deviation = selectedEdge
        ?.takeIf { PlanScale.isMismatched(plan, it.id) }
        ?.let { edge -> PlanScale.deviationMillimeters(plan, edge.id)?.let { formatLength(it) } }

    BackHandler {
        commitDimension()
        onBack()
    }
    // The card closes before the editor leaves: a back press while typing must not lose the plan
    // screen. Declared after the exit handler, which is the one that wins when enabled.
    BackHandler(enabled = cardOpen && selectedEdge != null && tool == PlanTool.Dimensions) {
        closeCard()
    }
    // An unfinished measurement is cancelled first; a second back press leaves the plan.
    BackHandler(enabled = calculationMode != null) {
        cancelCalculation()
    }

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
                    IconButton(
                        onClick = {
                            if (calculationMode != null) {
                                cancelCalculation()
                            } else {
                                commitDimension()
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (calculationMode != null) {
                                    R.drawable.ic_close
                                } else {
                                    R.drawable.ic_arrow_back
                                },
                            ),
                            contentDescription = stringResource(
                                if (calculationMode != null) {
                                    R.string.action_cancel
                                } else {
                                    R.string.action_back
                                },
                            ),
                        )
                    }
                },
                actions = {
                    if (calculationMode == null) {
                        IconButton(
                            onClick = viewModel::undo,
                            enabled = canUndo,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_undo),
                                contentDescription = stringResource(R.string.action_undo),
                            )
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
                                // Undo is the only action left in the top bar, so every other
                                // plan action lives here; that keeps the plan name readable on a
                                // phone screen.
                                if (!viewport.isFitted) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.plan_fit_screen)) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_fit_screen),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            viewport = PlanViewport()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.plan_calculations)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_calculate),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        commitDimension()
                                        cardOpen = false
                                        calculationsOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.plan_grid)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_grid),
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = if (showGrid) {
                                        {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_check),
                                                contentDescription = null,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        menuOpen = false
                                        showGrid = !showGrid
                                    },
                                )
                                // Deleting the line itself stays in Edit: in Dimensions mode the
                                // card owns the delete action, so the same trash must not remove
                                // the wall behind it.
                                if (tool == PlanTool.Edit && selectedEdge != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.plan_delete_edge)) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_delete),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            selectedEdgeId = null
                                            cardOpen = false
                                            viewModel.removeEdge(selectedEdge.id)
                                        },
                                    )
                                }
                                HorizontalDivider()
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
                    } else if (!viewport.isFitted) {
                        IconButton(onClick = { viewport = PlanViewport() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fit_screen),
                                contentDescription = stringResource(R.string.plan_fit_screen),
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
            // The status and unit bars stay out of the measurement mode: the selection needs the
            // canvas, and the unit is chosen for the plan, not for one calculation.
            if (calculationMode == null) {
                // The rangefinder status stays visible while drawing: a reading goes only into an
                // open dimension card, and the user has to know whether the device is there at all.
                PlanConnectBanner(
                    connected = gattState.connected,
                    onConnect = onOpenConnect,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
                UnitBar(
                    unit = plan.unit,
                    onUnitChange = ::changeUnit,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    // The canvas fills this box, so its size is the canvas size used to place the
                    // dimension card over its wall.
                    .onSizeChanged { canvasSize = it },
            ) {
                PlanCanvas(
                    plan = plan,
                    tool = tool,
                    onSegment = viewModel::addSegment,
                    onMoveNode = viewModel::moveNode,
                    onMoveEdge = viewModel::moveEdge,
                    onSelectEdge = selectEdge,
                    onSplitEdge = splitEdge,
                    selectedEdgeId = selectedEdge?.id,
                    calculationMode = calculationMode,
                    calculationEdgeIds = calculationEdgeIds,
                    calculationSubtractHoles = subtractHoles,
                    onSelectCalculationArea = { calculationEdgeIds = it },
                    onToggleCalculationEdge = toggleCalculationEdge,
                    onClearCalculationSelection = clearCalculationSelection,
                    showGrid = showGrid,
                    viewport = viewport,
                    onViewportChange = { viewport = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (
                    calculationMode == null &&
                    tool == PlanTool.Dimensions &&
                    cardOpen &&
                    selectedEdge != null &&
                    cardAnchor != null
                ) {
                    PlanDimensionCardOverlay(
                        anchor = cardAnchor,
                        canvasSize = canvasSize,
                        edgeId = selectedEdge.id,
                        value = draftValue,
                        unit = plan.unit,
                        hasSavedDimension = selectedEdge.dimension != null,
                        connected = gattState.connected,
                        deviation = deviation,
                        onValueChange = { draftValue = it },
                        onDone = {
                            commitDimension()
                            focusManager.clearFocus()
                        },
                        onDelete = { viewModel.removeEdgeDimension(selectedEdge.id) },
                        onClose = ::closeCard,
                    )
                }
                if (calculationMode == null && !plan.hasDrawing) {
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
                if (calculationMode == null && tool == PlanTool.Edit && plan.hasDrawing) {
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
                if (
                    calculationMode == null &&
                    tool == PlanTool.Dimensions &&
                    plan.hasDrawing &&
                    selectedEdge == null
                ) {
                    Text(
                        text = stringResource(R.string.plan_dimension_hint),
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
            val activeCalculation = calculationMode
            if (activeCalculation != null) {
                PlanCalculationPanel(
                    plan = plan,
                    kind = activeCalculation,
                    edgeIds = calculationEdgeIds,
                    subtractHoles = subtractHoles,
                    onKindChange = { newKind ->
                        if (newKind != activeCalculation) {
                            calculationMode = newKind
                            calculationEdgeIds = emptySet()
                        }
                    },
                    onSubtractHolesChange = { subtractHoles = it },
                    onReset = clearCalculationSelection,
                    onSave = { showSaveCalculation = true },
                    onCancel = ::cancelCalculation,
                )
            } else {
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
                                    commitDimension()
                                    tool = value
                                    selectedEdgeId = null
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PlanTool.entries.size,
                                ),
                            ) {
                                // Labels only: three icon-and-text segments do not fit a phone
                                // width, and Russian mode names are longer than the English ones.
                                Text(
                                    text = stringResource(
                                        when (value) {
                                            PlanTool.Draw -> R.string.plan_tool_draw
                                            PlanTool.Edit -> R.string.plan_tool_edit
                                            PlanTool.Dimensions -> R.string.plan_tool_dimension
                                        },
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                        onDeletePlan(plan.id)
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

    val activeCalculation = calculationMode
    if (showSaveCalculation && activeCalculation != null) {
        val defaultName = when (activeCalculation) {
            PlanCalculationKind.AREA -> stringResource(
                R.string.plan_calculation_default_area,
                plan.calculations.count { it.kind == PlanCalculationKind.AREA } + 1,
            )
            PlanCalculationKind.LENGTH -> stringResource(
                R.string.plan_calculation_default_length,
                plan.calculations.count { it.kind == PlanCalculationKind.LENGTH } + 1,
            )
        }
        PlanCalculationNameDialog(
            title = stringResource(R.string.plan_calculation_save_title),
            initialName = defaultName,
            onConfirm = { name, comment ->
                showSaveCalculation = false
                viewModel.saveCalculation(
                    name = name,
                    kind = activeCalculation,
                    edgeIds = calculationEdgeIds,
                    subtractHoles = subtractHoles,
                    comment = comment,
                )
                cancelCalculation()
                calculationsOpen = true
            },
            onDismiss = { showSaveCalculation = false },
        )
    }

    if (calculationsOpen) {
        PlanCalculationsSheet(
            plan = plan,
            onDismiss = { calculationsOpen = false },
            onNew = ::startCalculation,
            onRename = viewModel::renameCalculation,
            onDelete = viewModel::deleteCalculation,
            onCommentChange = viewModel::setCalculationComment,
        )
    }

    pendingSplit?.let { split ->
        val dimension = "${Length.format(split.dimension.valueMm, plan.unit)} " +
            stringResource(plan.unit.labelRes())
        AlertDialog(
            onDismissRequest = { pendingSplit = null },
            title = { Text(stringResource(R.string.plan_split_title)) },
            text = { Text(stringResource(R.string.plan_split_text, dimension)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSplit = null
                        viewModel.splitEdge(split.edgeId, split.at)
                    },
                ) {
                    Text(stringResource(R.string.plan_split_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSplit = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Point insertion waiting for the user to confirm that the wall's dimension is removed. */
private data class PendingEdgeSplit(
    val edgeId: String,
    val at: PlanPoint,
    val dimension: PlanDimension,
)

/** Link status above the sheet; a reading is useful only while the rangefinder is connected. */
@Composable
private fun PlanConnectBanner(
    connected: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectBanner(
        title = stringResource(
            if (connected) {
                R.string.rangefinder_connected
            } else {
                R.string.rangefinder_disconnected
            },
        ),
        containerColor = if (connected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (connected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        actionLabel = if (connected) null else stringResource(R.string.rangefinder_connect),
        onAction = if (connected) null else onConnect,
        modifier = modifier,
    )
}
