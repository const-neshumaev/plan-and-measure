package com.planmeasure.survey

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planmeasure.R
import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.protocol.DistanceFrame
import com.planmeasure.settings.LastLengthUnitStorage
import com.planmeasure.units.Length
import com.planmeasure.units.LengthInput
import com.planmeasure.units.LengthUnit
import com.planmeasure.units.toCanonicalMillimeters
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the saved surveys, the photo store and the survey that is currently open.
 *
 * A survey is a photo sketch: lines are added and removed, dimensions are labels on lines, and
 * nothing reshapes the geometry or produces an area (ADR 0021). Every change is written
 * immediately; undo restores in-memory snapshots of the open document.
 *
 * Importing or replacing a photo is not an undoable sketch change: a photo can only be replaced
 * while the sketch is empty, so there is no sketch state to roll back (ADR 0022).
 */
class SurveyViewModel(app: Application) : AndroidViewModel(app) {

    private val dataDirectory = File(app.filesDir, SURVEY_DATA_DIRECTORY)
    private val repository = JsonSurveyRepository(
        directory = dataDirectory,
        photoDirectory = File(app.filesDir, SURVEY_PHOTOS_DIRECTORY),
    )
    private val photoStore = SurveyPhotoStore(app, surveyPhotoDirectory(app))
    private val lastUnit = LastLengthUnitStorage(app)

    private val persistence = Mutex()

    private val undoHistory = ArrayDeque<SurveyDocument>()

    val surveys = repository.surveys

    private val _openSurvey = MutableStateFlow<SurveyDocument?>(null)
    val openSurvey = _openSurvey.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching {
                photoStore.prepare()
                repository.reload()
            }.onFailure { report(R.string.survey_error_load) }
            _loading.value = false
        }
    }

    fun open(id: String) {
        viewModelScope.launch {
            persistence.withLock {
                val document = runCatching { repository.load(id) }.getOrNull()
                if (document == null) {
                    report(R.string.survey_error_load)
                } else {
                    resetUndo()
                    _openSurvey.value = document
                }
            }
        }
    }

    fun close() {
        resetUndo()
        _openSurvey.value = null
    }

    fun createSurvey(
        name: String,
        open: Boolean = true,
        onCreated: (String) -> Unit = {},
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = persistence.withLock {
                runCatching { repository.create(name, lastUnit.load()) }
                    .onSuccess { created ->
                        if (open) {
                            resetUndo()
                            _openSurvey.value = created
                        }
                    }
                    .onFailure { report(R.string.survey_error_create) }
                    .getOrNull()
            }
            if (created != null) onCreated(created.id)
        }
    }

    fun renameOpenSurvey(name: String) {
        val current = _openSurvey.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit(
            current.copy(name = trimmed, updatedAt = System.currentTimeMillis()),
            undoable = false,
        )
    }

    fun renameSurvey(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                val renamed = runCatching { repository.rename(id, name) }.getOrNull()
                when {
                    renamed == null -> report(R.string.survey_error_save)
                    _openSurvey.value?.id == id -> _openSurvey.value = renamed
                }
            }
        }
    }

    /**
     * Deletes the survey and calls [onDeleted] after the document and its photo are gone.
     *
     * The callback lets the project section drop references to the object without teaching the
     * survey domain about projects (ADR 0017).
     */
    fun deleteSurvey(id: String, onDeleted: () -> Unit = {}) {
        if (_openSurvey.value?.id == id) close()
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.delete(id) }
                    .onSuccess { onDeleted() }
                    .onFailure { report(R.string.survey_error_delete) }
            }
        }
    }

    /** Whether a survey document exists on disk, even if it cannot be read right now. */
    fun hasDocument(id: String): Boolean = repository.hasDocument(id)

    /** Decodes the stored photo for the list thumbnail or the editor. */
    suspend fun loadPhoto(surveyId: String, maxSidePx: Int): Bitmap? =
        photoStore.load(surveyId, maxSidePx)

    /** Applies one drawing gesture; [snapRadius] is in sheet units. */
    fun addSegment(from: SurveyPoint, to: SurveyPoint, snapRadius: Double) {
        val current = _openSurvey.value ?: return
        val updated = SurveyEditing.addSegment(current, from, to, snapRadius, newId())
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    fun undo() {
        val current = _openSurvey.value ?: return
        val previous = undoHistory.removeLastOrNull() ?: return
        _canUndo.value = undoHistory.isNotEmpty()
        commit(
            previous.copy(name = current.name, updatedAt = System.currentTimeMillis()),
            undoable = false,
        )
    }

    fun removeSegment(segmentId: String) {
        val current = _openSurvey.value ?: return
        val updated = SurveyEditing.removeSegment(current, segmentId)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Moves one end of [segmentId]; coincident ends of neighbouring lines move with it. */
    fun moveEndpoint(
        segmentId: String,
        endpoint: SurveyEndpoint,
        to: SurveyPoint,
        snapRadius: Double,
    ) {
        val current = _openSurvey.value ?: return
        val updated = SurveyEditing.moveEndpoint(current, segmentId, endpoint, to, snapRadius)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Slides a whole line by [delta], keeping its direction and length. */
    fun moveSegment(segmentId: String, delta: SurveyPoint) {
        val current = _openSurvey.value ?: return
        val updated = SurveyEditing.moveSegment(current, segmentId, delta)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Saves the value and comment of [segmentId].
     *
     * The text is validated and canonicalized in the survey's unit by [LengthInput]; blank or
     * invalid value keeps the previous dimension. A blank value with an existing dimension still
     * updates the comment. The source follows the value: changing only the comment of a
     * rangefinder reading keeps it a reading.
     */
    fun setSegmentDimension(segmentId: String, valueText: String, commentText: String) {
        val current = _openSurvey.value ?: return
        val existing = current.segments.firstOrNull { it.id == segmentId }?.dimension
        val valueMm = when {
            valueText.isBlank() -> existing?.valueMm ?: return
            existing != null && valueText.trim() == Length.format(existing.valueMm, current.unit) ->
                existing.valueMm

            else -> LengthInput.normalize(valueText, current.unit) ?: return
        }
        val source = if (existing != null && existing.valueMm == valueMm) {
            existing.source
        } else {
            MeasurementSource.MANUAL
        }
        applyDimension(segmentId, valueMm, source, commentText)
    }

    /**
     * Writes a decoded rangefinder reading into the dimension of [segmentId].
     *
     * The caller passes only the line whose card is open, so readings never land on a wall the user
     * is not looking at. Returns `true` when the reading is a valid value, so the screen can close
     * the card even when the same value was already there.
     */
    fun onRangefinderFrame(
        segmentId: String,
        frame: DistanceFrame,
        commentText: String,
    ): Boolean {
        val valueMm = frame.toCanonicalMillimeters() ?: return false
        applyDimension(segmentId, valueMm, MeasurementSource.RANGFINDER, commentText)
        return true
    }

    fun removeSegmentDimension(segmentId: String) {
        val current = _openSurvey.value ?: return
        val updated = SurveyEditing.removeDimension(current, segmentId)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Changes the unit of the open survey; the stored millimeters are not touched. */
    fun setUnit(unit: LengthUnit) {
        lastUnit.save(unit)
        val current = _openSurvey.value ?: return
        if (current.unit == unit) return
        commit(current.copy(unit = unit, updatedAt = System.currentTimeMillis()))
    }

    fun clearSketch() {
        val current = _openSurvey.value ?: return
        val updated = SurveyEditing.clearSketch(current)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Target file the system camera app writes to, or `null` without an open survey.
     *
     * The UI wraps it in a `FileProvider` URI; the file becomes the survey photo only after
     * [acceptCameraPhoto].
     */
    fun cameraTarget(): File? {
        val current = _openSurvey.value ?: return null
        return photoStore.prepareCameraTarget(current.id)
    }

    /** Promotes the photo left by the camera app; a failed import removes the temporary file. */
    fun acceptCameraPhoto() {
        val current = _openSurvey.value ?: return
        if (current.hasSketch) {
            photoStore.discardCameraTarget(current.id)
            return
        }
        viewModelScope.launch {
            val imported = runCatching { photoStore.importFromCamera(current.id) }
            imported.exceptionOrNull()?.let { Log.w("SurveyPhoto", "Camera import failed", it) }
            val photo = imported.getOrNull()
            if (photo == null) {
                report(R.string.survey_error_photo)
            } else {
                attachPhoto(current.id, photo)
            }
        }
    }

    /** Drops the temporary camera file after a cancelled capture. */
    fun discardCameraPhoto() {
        _openSurvey.value?.let { photoStore.discardCameraTarget(it.id) }
    }

    /** Reports that the photo could not be obtained, for example when no camera app is installed. */
    fun reportPhotoError() {
        report(R.string.survey_error_photo)
    }

    /** Copies a gallery image into the survey; a sketch must be empty to replace a photo. */
    fun importPhoto(uri: Uri, source: SurveyPhotoSource) {
        val current = _openSurvey.value ?: return
        if (current.hasSketch) return
        viewModelScope.launch {
            val photo = runCatching { photoStore.importFromUri(current.id, uri, source) }
                .getOrNull()
            if (photo == null) {
                report(R.string.survey_error_photo)
            } else {
                attachPhoto(current.id, photo)
            }
        }
    }

    private fun attachPhoto(surveyId: String, photo: SurveyPhoto) {
        val current = _openSurvey.value?.takeIf { it.id == surveyId } ?: return
        val updated = SurveyEditing.setPhoto(current, photo)
            .copy(updatedAt = System.currentTimeMillis())
        // The photo is not an undoable sketch change: replacing it is allowed only without a
        // sketch, and the previous file is overwritten. Undo snapshots could point at the old
        // photo metadata while the file already holds the new image, so they are dropped.
        resetUndo()
        _openSurvey.value = updated
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.update(updated) }
                    .onFailure { report(R.string.survey_error_save) }
            }
        }
    }

    private fun applyDimension(
        segmentId: String,
        valueMm: String,
        source: MeasurementSource,
        commentText: String,
    ) {
        val current = _openSurvey.value ?: return
        val segment = current.segments.firstOrNull { it.id == segmentId } ?: return
        val comment = commentText.trim()
        val existing = segment.dimension
        if (existing != null && existing.valueMm == valueMm && existing.comment == comment) return
        val now = System.currentTimeMillis()
        val updated = SurveyEditing.setDimension(
            document = current,
            segmentId = segmentId,
            dimension = SurveyDimension(
                valueMm = valueMm,
                source = source,
                updatedAt = now,
                comment = comment,
            ),
        )
        if (updated === current) return
        commit(updated.copy(updatedAt = now))
    }

    private fun commit(survey: SurveyDocument, undoable: Boolean = true) {
        val previous = _openSurvey.value
        if (undoable && previous != null && previous != survey) rememberUndo(previous)
        _openSurvey.value = survey
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.update(survey) }
                    .onFailure { report(R.string.survey_error_save) }
            }
        }
    }

    private fun rememberUndo(document: SurveyDocument) {
        undoHistory.addLast(document)
        while (undoHistory.size > UNDO_LIMIT) undoHistory.removeFirst()
        _canUndo.value = true
    }

    private fun resetUndo() {
        undoHistory.clear()
        _canUndo.value = false
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun report(@StringRes id: Int) {
        _messages.tryEmit(getApplication<Application>().getString(id))
    }

    private companion object {
        const val UNDO_LIMIT = 50
    }
}
