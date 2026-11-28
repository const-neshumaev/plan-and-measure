package com.planmeasure.ui.survey

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.ui.plans.PlanViewport
import com.planmeasure.ui.plans.PlanViewportSaver
import com.planmeasure.ui.plans.SheetMargin
import com.planmeasure.survey.SurveyDocument
import com.planmeasure.survey.SurveyPhotoSource
import com.planmeasure.survey.SurveyViewModel
import com.planmeasure.ui.components.ConnectBanner
import com.planmeasure.ui.components.ListNameDialog
import com.planmeasure.ui.components.UnitBar
import com.planmeasure.units.Length
import com.planmeasure.units.LengthUnit

/** Editor of one survey: the photo with the sketch, the tool switch, undo and the sketch menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyEditorScreen(
    survey: SurveyDocument,
    viewModel: SurveyViewModel,
    labViewModel: LabViewModel,
    onOpenConnect: () -> Unit,
    onDeleteSurvey: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val canUndo by viewModel.canUndo.collectAsState()
    val gattState by labViewModel.gattState.collectAsState()
    val focusManager = LocalFocusManager.current

    var tool by rememberSaveable { mutableStateOf(SurveyTool.Draw) }
    var selectedSegmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var cardOpen by rememberSaveable { mutableStateOf(false) }
    var draftValue by rememberSaveable { mutableStateOf("") }
    var draftComment by rememberSaveable { mutableStateOf("") }
    // The view starts fitted for every survey and is not part of the document.
    var viewport by rememberSaveable(survey.id, stateSaver = PlanViewportSaver) {
        mutableStateOf(PlanViewport())
    }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val imageState = rememberSurveyImage(survey.id, survey.photo, viewModel)
    val selectedSegment = survey.segments.firstOrNull { it.id == selectedSegmentId }

    // The fields follow the document, so a rangefinder reading, an undo or a unit change replaces
    // the shown value; typing cannot trigger this, because the document changes only on commit.
    LaunchedEffect(selectedSegment?.id, selectedSegment?.dimension, survey.unit) {
        draftValue = selectedSegment?.dimension
            ?.let { Length.format(it.valueMm, survey.unit) }
            .orEmpty()
        draftComment = selectedSegment?.dimension?.comment.orEmpty()
    }

    fun commitDimension() {
        if (tool != SurveyTool.Dimensions || !cardOpen) return
        val segmentId = selectedSegmentId ?: return
        viewModel.setSegmentDimension(segmentId, draftValue, draftComment)
    }

    fun changeUnit(unit: LengthUnit) {
        if (unit == survey.unit) return
        commitDimension()
        viewModel.setUnit(unit)
    }

    fun closeCard() {
        commitDimension()
        cardOpen = false
    }

    // Selecting another line closes the card and keeps what was typed. Tapping the selected line
    // again opens the card a reading closed; tapping empty space drops the selection.
    val selectSegment: (String?) -> Unit = { id ->
        when {
            id == null -> {
                commitDimension()
                cardOpen = false
                selectedSegmentId = null
            }

            id == selectedSegmentId -> cardOpen = tool == SurveyTool.Dimensions
            else -> {
                commitDimension()
                selectedSegmentId = id
                cardOpen = tool == SurveyTool.Dimensions
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.importPhoto(uri, SurveyPhotoSource.GALLERY)
    }
    // A hand-built capture intent instead of ActivityResultContracts.TakePicture: some camera apps
    // open EXTRA_OUTPUT with read-write access, so both URI grants are needed. The target file is
    // created before the launch for the same reason.
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.acceptCameraPhoto()
        } else {
            viewModel.discardCameraPhoto()
        }
    }

    fun takePhoto() {
        val target = viewModel.cameraTarget() ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
        }.getOrNull()
        if (uri == null) {
            viewModel.discardCameraPhoto()
            viewModel.reportPhotoError()
            return
        }
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        runCatching { takePicture.launch(intent) }
            .onFailure {
                viewModel.discardCameraPhoto()
                viewModel.reportPhotoError()
            }
    }

    fun choosePhoto() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // A reading goes only to the line whose card is open: pointing the device at another wall
    // while the card is closed must not silently rewrite a dimension.
    val liveComment = rememberUpdatedState(draftComment)
    val lifecycleOwner = LocalLifecycleOwner.current
    val dimensionSegmentId = selectedSegment?.id
        ?.takeIf { tool == SurveyTool.Dimensions && cardOpen }
    LaunchedEffect(dimensionSegmentId, lifecycleOwner) {
        val segmentId = dimensionSegmentId ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            labViewModel.deviceFrames.collect { frame ->
                if (viewModel.onRangefinderFrame(segmentId, frame, liveComment.value)) {
                    // The reading is the value now, so there is nothing left to type. Hiding the
                    // keyboard and the card leaves the line free for the next measurement.
                    focusManager.clearFocus()
                    cardOpen = false
                }
            }
        }
    }

    val density = LocalDensity.current
    val sheetPadding = with(density) { SheetMargin.toPx() }
    val cardAnchor = remember(survey, viewport, canvasSize, selectedSegment?.id) {
        selectedSegment?.let { segment ->
            surveyCardAnchor(canvasSize, survey, segment.id, viewport, sheetPadding)
        }
    }

    BackHandler {
        commitDimension()
        onBack()
    }
    // The card closes before the editor leaves: a back press while typing must not lose the screen.
    BackHandler(enabled = cardOpen && selectedSegment != null && tool == SurveyTool.Dimensions) {
        closeCard()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = survey.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            commitDimension()
                            onBack()
                        },
                    ) {
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
                            // A photo can be replaced only while nothing has been drawn on it:
                            // otherwise every line would silently move off its detail.
                            if (imageState is SurveyImageState.Ready && !survey.hasSketch) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.survey_take_new_photo))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_section_capture),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        takePhoto()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.survey_choose_another_photo))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_section_capture),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        choosePhoto()
                                    },
                                )
                            }
                            if (selectedSegment != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.survey_delete_segment)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        cardOpen = false
                                        selectedSegmentId = null
                                        viewModel.removeSegment(selectedSegment.id)
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
                                text = { Text(stringResource(R.string.survey_clear)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                    )
                                },
                                enabled = survey.hasSketch,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SurveyConnectBanner(
                connected = gattState.connected,
                onConnect = onOpenConnect,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            UnitBar(
                unit = survey.unit,
                onUnitChange = ::changeUnit,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { canvasSize = it },
            ) {
                when (val state = imageState) {
                    SurveyImageState.Loading -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.survey_photo_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    SurveyImageState.Missing -> SurveyPhotoEmptyState(
                        missingFile = survey.photo != null,
                        onTakePhoto = ::takePhoto,
                        onChoosePhoto = ::choosePhoto,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is SurveyImageState.Ready -> {
                        SurveyCanvas(
                            survey = survey,
                            photo = state.image,
                            tool = tool,
                            selectedSegmentId = selectedSegment?.id,
                            viewport = viewport,
                            onViewportChange = { viewport = it },
                            onAddSegment = viewModel::addSegment,
                            onMoveEndpoint = viewModel::moveEndpoint,
                            onMoveSegment = viewModel::moveSegment,
                            onSelectSegment = selectSegment,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!survey.hasSketch) {
                            SurveyHint(
                                text = stringResource(R.string.survey_draw_hint),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp),
                            )
                        } else if (tool == SurveyTool.Edit) {
                            SurveyHint(
                                text = stringResource(R.string.survey_edit_hint),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp),
                            )
                        } else if (tool == SurveyTool.Dimensions && selectedSegment == null) {
                            SurveyHint(
                                text = stringResource(R.string.survey_dimension_hint),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp),
                            )
                        }
                        if (
                            tool == SurveyTool.Dimensions &&
                            cardOpen &&
                            selectedSegment != null &&
                            cardAnchor != null
                        ) {
                            SurveyDimensionCardOverlay(
                                anchor = cardAnchor,
                                canvasSize = canvasSize,
                                segmentId = selectedSegment.id,
                                value = draftValue,
                                comment = draftComment,
                                unit = survey.unit,
                                hasSavedDimension = selectedSegment.dimension != null,
                                connected = gattState.connected,
                                onValueChange = { draftValue = it },
                                onCommentChange = { draftComment = it },
                                onDone = {
                                    commitDimension()
                                    focusManager.clearFocus()
                                },
                                onDelete = {
                                    viewModel.removeSegmentDimension(selectedSegment.id)
                                },
                                onClose = ::closeCard,
                            )
                        }
                    }
                }
            }
            if (imageState is SurveyImageState.Ready) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SingleChoiceSegmentedButtonRow {
                        SurveyTool.entries.forEachIndexed { index, value ->
                            SegmentedButton(
                                selected = tool == value,
                                onClick = {
                                    commitDimension()
                                    tool = value
                                    selectedSegmentId = null
                                    cardOpen = false
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SurveyTool.entries.size,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        when (value) {
                                            SurveyTool.Draw -> R.string.survey_tool_draw
                                            SurveyTool.Edit -> R.string.survey_tool_edit
                                            SurveyTool.Dimensions -> R.string.survey_tool_dimension
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
            title = stringResource(R.string.survey_rename_title),
            labelRes = R.string.survey_name,
            hintRes = R.string.survey_name_hint,
            initialName = survey.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                showRename = false
                viewModel.renameOpenSurvey(name)
            },
            onDismiss = { showRename = false },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.survey_clear_title)) },
            text = { Text(stringResource(R.string.survey_clear_text, survey.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClear = false
                        selectedSegmentId = null
                        cardOpen = false
                        viewModel.clearSketch()
                    },
                ) {
                    Text(stringResource(R.string.survey_clear))
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
            title = { Text(stringResource(R.string.survey_delete_title)) },
            text = { Text(stringResource(R.string.survey_delete_text, survey.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        onDeleteSurvey(survey.id)
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

@Composable
private fun SurveyConnectBanner(
    connected: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectBanner(
        title = stringResource(
            if (connected) R.string.rangefinder_connected else R.string.rangefinder_disconnected,
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

@Composable
private fun SurveyPhotoEmptyState(
    missingFile: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_section_capture),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (missingFile) R.string.survey_photo_missing else R.string.survey_photo_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.survey_photo_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onTakePhoto) {
            Text(stringResource(R.string.survey_take_photo))
        }
        OutlinedButton(onClick = onChoosePhoto) {
            Text(stringResource(R.string.survey_choose_photo))
        }
    }
}

@Composable
private fun SurveyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
