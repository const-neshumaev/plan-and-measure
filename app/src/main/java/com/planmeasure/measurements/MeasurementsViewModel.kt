package com.planmeasure.measurements

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planmeasure.R
import com.planmeasure.protocol.DistanceFrame
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
 */
class MeasurementsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = JsonMeasurementListRepository(File(app.filesDir, DATA_DIRECTORY))

    private val persistence = Mutex()

    val lists = repository.lists

    private val _openList = MutableStateFlow<MeasurementList?>(null)
    val openList = _openList.asStateFlow()

    private val _capturePaused = MutableStateFlow(false)
    val capturePaused = _capturePaused.asStateFlow()

    /** Default unit for manual entry during this app session; not persisted yet. */
    private val _inputUnit = MutableStateFlow(LengthUnit.METERS)
    val inputUnit = _inputUnit.asStateFlow()

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
    }

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.create(name) }
                    .onSuccess {
                        _capturePaused.value = false
                        _openList.value = it
                    }
                    .onFailure { report(R.string.measurement_error_create) }
            }
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

    fun deleteOpenList() {
        val current = _openList.value ?: return
        deleteList(current.id)
    }

    fun deleteList(id: String) {
        // Stop recording before the delete is queued: a frame arriving in between must not
        // write the document back after the file has been removed.
        if (_openList.value?.id == id) close()
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.delete(id) }
                    .onFailure { report(R.string.measurement_error_delete) }
            }
        }
    }

    fun addMeasurement(valueText: String, unit: LengthUnit, comment: String) {
        val normalized = MeasurementInput.normalize(valueText) ?: return
        append(
            Measurement(
                id = UUID.randomUUID().toString(),
                valueText = normalized,
                unit = unit,
                comment = comment.trim(),
                createdAt = System.currentTimeMillis(),
                source = MeasurementSource.MANUAL,
            ),
        )
    }

    fun updateMeasurement(id: String, valueText: String, unit: LengthUnit, comment: String) {
        val normalized = MeasurementInput.normalize(valueText) ?: return
        val current = _openList.value ?: return
        if (current.measurements.none { it.id == id }) return
        val updated = current.measurements.map { measurement ->
            if (measurement.id == id) {
                measurement.copy(
                    valueText = normalized,
                    unit = unit,
                    comment = comment.trim(),
                )
            } else {
                measurement
            }
        }
        commit(current.copy(updatedAt = System.currentTimeMillis(), measurements = updated))
    }

    /** Removes a measurement and returns it so the caller can offer Undo. */
    fun deleteMeasurement(id: String): Measurement? {
        val current = _openList.value ?: return null
        val removed = current.measurements.firstOrNull { it.id == id } ?: return null
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
        val unit = frame.unit.toLengthUnit() ?: return
        val measurement = Measurement(
            id = UUID.randomUUID().toString(),
            valueText = frame.normalizedText,
            unit = unit,
            createdAt = System.currentTimeMillis(),
            source = MeasurementSource.RANGFINDER,
        )
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

    fun setInputUnit(unit: LengthUnit) {
        _inputUnit.value = unit
    }

    private fun append(measurement: Measurement) {
        val current = _openList.value ?: return
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
