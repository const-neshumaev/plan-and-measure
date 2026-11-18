package com.planmeasure.ui.measurements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.measurements.Measurement
import com.planmeasure.measurements.MeasurementList
import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.measurements.MeasurementsViewModel
import com.planmeasure.units.Length
import com.planmeasure.units.LengthUnit
import com.planmeasure.ui.components.ConnectBanner
import com.planmeasure.ui.components.EmptyState
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.UnitBar
import com.planmeasure.ui.components.labelRes
import java.util.Locale
import kotlinx.coroutines.launch

/** Editor of one saved list: capture status, measurements and the manual input sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementListScreen(
    list: MeasurementList,
    viewModel: MeasurementsViewModel,
    labViewModel: LabViewModel,
    onOpenConnect: () -> Unit,
    onBack: () -> Unit,
) {
    val gattState by labViewModel.gattState.collectAsState()
    val capturePaused by viewModel.capturePaused.collectAsState()
    val lastAddedId by viewModel.lastAddedMeasurementId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDeleteList by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusComment by rememberSaveable { mutableStateOf(false) }

    val editing = editingId?.let { id -> list.measurements.firstOrNull { it.id == id } }

    BackHandler(enabled = sheetOpen) { sheetOpen = false }
    BackHandler(enabled = !sheetOpen) { onBack() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Collection stops when this screen leaves the composition and while the app is in the
    // background, which scopes device recording to the visible open list (see ADR 0003).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(capturePaused, lifecycleOwner) {
        if (capturePaused) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            labViewModel.deviceFrames.collect { frame -> viewModel.onRangefinderFrame(frame) }
        }
    }

    val deleteMeasurement: (Measurement) -> Unit = { measurement ->
        viewModel.deleteMeasurement(measurement.id)?.let { removed ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.measurement_deleted),
                    actionLabel = context.getString(R.string.action_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.restoreMeasurement(removed)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = list.name,
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
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDeleteList = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingId = null
                    focusComment = false
                    sheetOpen = true
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.measurement_add_manual)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
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
            item {
                CaptureBanner(
                    connected = gattState.connected,
                    paused = capturePaused,
                    onConnect = onOpenConnect,
                    onTogglePause = { viewModel.setCapturePaused(!capturePaused) },
                )
            }

            item {
                UnitBar(unit = list.unit, onUnitChange = viewModel::setUnit)
            }

            if (list.measurements.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.measurement_empty_title),
                        text = stringResource(R.string.measurement_empty_text),
                        actionLabel = stringResource(R.string.measurement_add_manual),
                        onAction = {
                            editingId = null
                            focusComment = false
                            sheetOpen = true
                        },
                    )
                }
            } else {
                items(
                    items = list.measurements.sortedByDescending { it.createdAt },
                    key = { it.id },
                ) { measurement ->
                    MeasurementRow(
                        measurement = measurement,
                        unit = list.unit,
                        locale = locale,
                        highlighted = measurement.id == lastAddedId,
                        onEdit = {
                            editingId = measurement.id
                            focusComment = false
                            sheetOpen = true
                        },
                        onEditComment = {
                            editingId = measurement.id
                            focusComment = true
                            sheetOpen = true
                        },
                        onDelete = { deleteMeasurement(measurement) },
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        MeasurementEditorSheet(
            editing = editing,
            unit = list.unit,
            focusComment = focusComment,
            onDismiss = { sheetOpen = false },
            onAdd = { value, comment -> viewModel.addMeasurement(value, comment) },
            onUpdate = { value, comment ->
                editing?.let { viewModel.updateMeasurement(it.id, value, comment) }
                sheetOpen = false
            },
        )
    }

    if (showRename) {
        ListNameDialog(
            title = stringResource(R.string.measurement_list_rename_title),
            labelRes = R.string.measurement_list_name,
            hintRes = R.string.measurement_list_name_hint,
            initialName = list.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                showRename = false
                viewModel.renameOpenList(name)
            },
            onDismiss = { showRename = false },
        )
    }

    if (showDeleteList) {
        AlertDialog(
            onDismissRequest = { showDeleteList = false },
            title = { Text(stringResource(R.string.measurement_list_delete_title)) },
            text = { Text(stringResource(R.string.measurement_list_delete_text, list.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteList = false
                        viewModel.deleteOpenList()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteList = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CaptureBanner(
    connected: Boolean,
    paused: Boolean,
    onConnect: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val container = when {
        !connected -> MaterialTheme.colorScheme.surfaceVariant
        paused -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        !connected -> MaterialTheme.colorScheme.onSurfaceVariant
        paused -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val title = stringResource(
        when {
            !connected -> R.string.rangefinder_disconnected
            paused -> R.string.measurement_capture_paused
            else -> R.string.rangefinder_connected
        },
    )
    val action = stringResource(
        when {
            !connected -> R.string.rangefinder_connect
            paused -> R.string.measurement_capture_resume
            else -> R.string.measurement_capture_pause
        },
    )

    ConnectBanner(
        title = title,
        containerColor = container,
        contentColor = content,
        actionLabel = action,
        onAction = if (connected) onTogglePause else onConnect,
    )
}

@Composable
private fun MeasurementRow(
    measurement: Measurement,
    unit: LengthUnit,
    locale: Locale,
    highlighted: Boolean,
    onEdit: () -> Unit,
    onEditComment: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val sourceIcon = if (measurement.source == MeasurementSource.RANGFINDER) {
        R.drawable.ic_bluetooth
    } else {
        R.drawable.ic_edit
    }
    val sourceLabel = stringResource(
        if (measurement.source == MeasurementSource.RANGFINDER) {
            R.string.measurement_source_rangefinder
        } else {
            R.string.measurement_source_manual
        },
    )

    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 10.dp,
                bottom = 10.dp,
                end = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(sourceIcon),
                contentDescription = sourceLabel,
                modifier = Modifier.size(if (highlighted) 22.dp else 18.dp),
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Length.format(measurement.valueMm, unit),
                        style = if (highlighted) {
                            MaterialTheme.typography.displaySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                    )
                    Text(
                        text = stringResource(unit.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                    )
                }
                if (measurement.comment.isNotBlank()) {
                    Text(
                        text = measurement.comment,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditComment)
                            .padding(vertical = 2.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.measurement_comment_add),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditComment)
                            .padding(vertical = 2.dp),
                    )
                }
            }
            Text(
                text = formatMeasurementTime(measurement.createdAt, locale = locale),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
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
