package com.planmeasure.measurements

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planmeasure.R
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
 * Owns the saved measurement lists.
 *
 * This ViewModel knows nothing about GATT: it accepts already decoded frames from the screen
 * that is currently recording. All writes are queued through one [Mutex] so that a burst of
 * measurements cannot reorder or overwrite a recent edit.
 *
 * Every measurement belongs to one list with a single unit (ADR 0009): input is validated in that
 * unit, device readings and stored values are canonical millimeters, and changing the unit only
 * changes how the same values are shown.
 */
class MeasurementsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = JsonMeasurementListRepository(File(app.filesDir, DATA_DIRECTORY))

    private val lastUnit = LastLengthUnitStorage(app)

    private val persistence = Mutex()

    val lists = repository.lists

    private val _openList = MutableStateFlow<MeasurementList?>(null)
    val openList = _openList.asStateFlow()

    private val _capturePaused = MutableStateFlow(false)
    val capturePaused = _capturePaused.asStateFlow()

    private val _lastAddedMeasurementId = MutableStateFlow<String?>(null)

    /**
     * Measurement added during this editing session, if any.
     *
     * The list screen makes its row stand out so the newest reading is unmistakable. Opening a
     * list clears the marker: after reopening nothing is highlighted until another value arrives.
     * Editing or restoring a value is not an addition and does not move the marker.
     */
    val lastAddedMeasurementId = _lastAddedMeasurementId.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.reload() }
                .onFailure { report(R.string.measurement_error_load) }
            _loading.value = false
        }
    }

    fun open(id: String) {
        _lastAddedMeasurementId.value = null
        viewModelScope.launch {
            persistence.withLock {
                val document = runCatching { repository.load(id) }.getOrNull()
                if (document == null) {
                    report(R.string.measurement_error_load)
                } else {
                    _capturePaused.value = false
                    _openList.value = document
                }
            }
        }
    }

    fun close() {
        _openList.value = null
        _capturePaused.value = false
        _lastAddedMeasurementId.value = null
    }

    /**
     * Creates a list.
     *
     * [open] is false when the list is created from a project and the user stays in the tree;
     * [onCreated] receives the id so the caller can attach the new document immediately.
     */
    fun createList(
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
                            _capturePaused.value = false
                            _lastAddedMeasurementId.value = null
                            _openList.value = created
                        }
                    }
                    .onFailure { report(R.string.measurement_error_create) }
                    .getOrNull()
            }
            if (created != null) onCreated(created.id)
        }
    }

    fun renameOpenList(name: String) {
        val current = _openList.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit(current.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    }

    fun renameList(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                val renamed = runCatching { repository.rename(id, name) }.getOrNull()
                when {
                    renamed == null -> report(R.string.measurement_error_save)
                    _openList.value?.id == id -> _openList.value = renamed
                }
            }
        }
    }

    /**
     * Deletes the list and calls [onDeleted] after the file is gone.
     *
     * The callback lets the project section drop references to the object without teaching the
     * measurement domain about projects (ADR 0017).
     */
    fun deleteList(id: String, onDeleted: () -> Unit = {}) {
        // Stop recording before the delete is queued: a frame arriving in between must not
        // write the document back after the file has been removed.
        if (_openList.value?.id == id) close()
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.delete(id) }
                    .onSuccess { onDeleted() }
                    .onFailure { report(R.string.measurement_error_delete) }
            }
        }
    }

    /** Whether a list document exists on disk, even if it cannot be read right now. */
    fun hasDocument(id: String): Boolean = repository.hasDocument(id)

    /** Adds a manually entered value of the open list; the unit is the list's own. */
    fun addMeasurement(valueText: String, comment: String) {
        val current = _openList.value ?: return
        val valueMm = LengthInput.normalize(valueText, current.unit) ?: return
        append(
            Measurement(
                id = UUID.randomUUID().toString(),
                valueMm = valueMm,
                comment = comment.trim(),
                createdAt = System.currentTimeMillis(),
                source = MeasurementSource.MANUAL,
            ),
        )
    }

    fun updateMeasurement(id: String, valueText: String, comment: String) {
        val current = _openList.value ?: return
        val measurement = current.measurements.firstOrNull { it.id == id } ?: return
        // An untouched field shows the rounded display form of the exact stored value. Saving it
        // back must not replace the exact value with its rounded form.
        val valueMm = if (valueText.trim() == Length.format(measurement.valueMm, current.unit)) {
            measurement.valueMm
        } else {
            LengthInput.normalize(valueText, current.unit) ?: return
        }
        val updated = current.measurements.map { existing ->
            if (existing.id == id) {
                existing.copy(
                    valueMm = valueMm,
                    comment = comment.trim(),
                )
            } else {
                existing
            }
        }
        commit(current.copy(updatedAt = System.currentTimeMillis(), measurements = updated))
    }

    /** Removes a measurement and returns it so the caller can offer Undo. */
    fun deleteMeasurement(id: String): Measurement? {
        val current = _openList.value ?: return null
        val removed = current.measurements.firstOrNull { it.id == id } ?: return null
        if (_lastAddedMeasurementId.value == id) _lastAddedMeasurementId.value = null
        commit(
            current.copy(
                updatedAt = System.currentTimeMillis(),
                measurements = current.measurements.filterNot { it.id == id },
            ),
        )
        return removed
    }

    fun restoreMeasurement(measurement: Measurement) {
        val current = _openList.value ?: return
        if (current.measurements.any { it.id == measurement.id }) return
        commit(
            current.copy(
                updatedAt = System.currentTimeMillis(),
                measurements = current.measurements + measurement,
            ),
        )
    }

    /** Adds a decoded device frame to the open list unless recording is paused. */
    fun onRangefinderFrame(frame: DistanceFrame) {
        if (_capturePaused.value) return
        val current = _openList.value ?: return
        val valueMm = frame.toCanonicalMillimeters() ?: return
        val measurement = Measurement(
            id = UUID.randomUUID().toString(),
            valueMm = valueMm,
            createdAt = System.currentTimeMillis(),
            source = MeasurementSource.RANGFINDER,
        )
        _lastAddedMeasurementId.value = measurement.id
        commit(
            current.copy(
                updatedAt = measurement.createdAt,
                measurements = current.measurements + measurement,
            ),
        )
    }

    fun setCapturePaused(paused: Boolean) {
        if (_openList.value == null) return
        _capturePaused.value = paused
    }

    /** Changes the unit of the open list; the stored millimeters are not touched. */
    fun setUnit(unit: LengthUnit) {
        lastUnit.save(unit)
        val current = _openList.value ?: return
        if (current.unit == unit) return
        commit(current.copy(unit = unit, updatedAt = System.currentTimeMillis()))
    }

    private fun append(measurement: Measurement) {
        val current = _openList.value ?: return
        _lastAddedMeasurementId.value = measurement.id
        commit(
            current.copy(
                updatedAt = measurement.createdAt,
                measurements = current.measurements + measurement,
            ),
        )
    }

    private fun commit(list: MeasurementList) {
        _openList.value = list
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.update(list) }
                    .onFailure { report(R.string.measurement_error_save) }
            }
        }
    }

    private fun report(@StringRes id: Int) {
        _messages.tryEmit(getApplication<Application>().getString(id))
    }

    private companion object {
        const val DATA_DIRECTORY = "measurements"
    }
}
