package com.planmeasure.plans

import android.app.Application
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
 * Owns the saved plans and the plan that is currently being drawn.
 *
 * Gestures arrive as raw points with the magnet radius used by the canvas; snapping and editing
 * are delegated to [PlanEditing]. Every change is written immediately, but only once per gesture,
 * not on every pointer move.
 *
 * Undo restores snapshots of the open document. The history is kept in memory only: it starts
 * empty when a plan is opened, so the undo button means "undo the last change of this session".
 *
 * Dimensions are stored on edges and give the plan its scale: the first one declares how many
 * millimeters a grid unit stands for, and the later ones reshape the drawing, see [PlanScale] and
 * ADR 0012. Values are canonical millimeters, and the plan's unit decides how they are shown
 * (ADR 0009).
 */
class PlansViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = JsonPlanRepository(File(app.filesDir, DATA_DIRECTORY))

    private val lastUnit = LastLengthUnitStorage(app)

    private val persistence = Mutex()

    private val undoHistory = ArrayDeque<PlanDocument>()

    val plans = repository.plans

    private val _openPlan = MutableStateFlow<PlanDocument?>(null)
    val openPlan = _openPlan.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.reload() }
                .onFailure { report(R.string.plan_error_load) }
            _loading.value = false
        }
    }

    fun open(id: String) {
        viewModelScope.launch {
            persistence.withLock {
                val document = runCatching { repository.load(id) }.getOrNull()
                if (document == null) {
                    report(R.string.plan_error_load)
                } else {
                    resetUndo()
                    // A document written while the sheet size was still capped can contain nodes
                    // outside the sheet; opening it is the moment to bring the sheet up to date.
                    val fitted = PlanEditing.fitWorldToDrawing(document)
                    if (fitted !== document) {
                        runCatching { repository.update(fitted) }
                    }
                    _openPlan.value = fitted
                }
            }
        }
    }

    fun close() {
        resetUndo()
        _openPlan.value = null
    }

    fun createPlan(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.create(name, lastUnit.load()) }
                    .onSuccess {
                        resetUndo()
                        _openPlan.value = it
                    }
                    .onFailure { report(R.string.plan_error_create) }
            }
        }
    }

    fun renameOpenPlan(name: String) {
        val current = _openPlan.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit(
            current.copy(name = trimmed, updatedAt = System.currentTimeMillis()),
            undoable = false,
        )
    }

    fun renamePlan(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                val renamed = runCatching { repository.rename(id, name) }.getOrNull()
                when {
                    renamed == null -> report(R.string.plan_error_save)
                    _openPlan.value?.id == id -> _openPlan.value = renamed
                }
            }
        }
    }

    fun deleteOpenPlan() {
        val current = _openPlan.value ?: return
        deletePlan(current.id)
    }

    fun deletePlan(id: String) {
        // Stop editing before the delete is queued: a gesture arriving in between must not write
        // the document back after the file has been removed.
        if (_openPlan.value?.id == id) close()
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.delete(id) }
                    .onFailure { report(R.string.plan_error_delete) }
            }
        }
    }

    /** Applies one drawing gesture; [magnetRadius] is in world units. */
    fun addSegment(from: PlanPoint, to: PlanPoint, magnetRadius: Double) {
        val current = _openPlan.value ?: return
        val updated = PlanEditing.addSegment(current, from, to, magnetRadius, ::newId)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    fun undo() {
        val current = _openPlan.value ?: return
        val previous = undoHistory.removeLastOrNull() ?: return
        _canUndo.value = undoHistory.isNotEmpty()
        // Renaming is not undoable, so only the geometry is rolled back, not the current name.
        commit(
            previous.copy(name = current.name, updatedAt = System.currentTimeMillis()),
            undoable = false,
        )
    }

    /** Applies one node drag; [to] is a raw world point and is snapped by [PlanEditing]. */
    fun moveNode(nodeId: String, to: PlanPoint, magnetRadius: Double) {
        val current = _openPlan.value ?: return
        val updated = PlanEditing.moveNode(current, nodeId, to, magnetRadius)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Applies one edge drag; [from] is the grabbed point of the edge and [to] a raw world point.
     * Snapping and validation are delegated to [PlanEditing].
     */
    fun moveEdge(edgeId: String, from: PlanPoint, to: PlanPoint) {
        val current = _openPlan.value ?: return
        val updated = PlanEditing.moveEdge(current, edgeId, from, to)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    fun removeEdge(edgeId: String) {
        val current = _openPlan.value ?: return
        val updated = PlanEditing.removeEdge(current, edgeId, ::newId)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Inserts a node on [edgeId] at [at], splitting the edge into two independent edges.
     *
     * The split is a normal undoable change, so undoing it brings back the edge and the dimension
     * the split removed. The warning about the lost dimension belongs to the screen: it knows
     * whether the picked edge carries one before asking for the split.
     */
    fun splitEdge(edgeId: String, at: PlanPoint) {
        val current = _openPlan.value ?: return
        val updated = PlanEditing.splitEdge(current, edgeId, at, ::newId)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Saves a manually entered dimension on [edgeId].
     *
     * The text is validated and canonicalized in the plan's unit by [LengthInput]; blank or
     * invalid input is ignored, so a card dismissed with a half-typed value keeps the previous
     * dimension.
     */
    fun setEdgeDimension(edgeId: String, valueText: String) {
        val current = _openPlan.value ?: return
        val existing = current.edges.firstOrNull { it.id == edgeId }?.dimension
        // An untouched card shows the rounded display form of the exact stored value. Closing it
        // must not replace the exact value with its rounded form.
        val valueMm = when {
            existing != null && valueText.trim() == Length.format(existing.valueMm, current.unit) ->
                existing.valueMm
            else -> LengthInput.normalize(valueText, current.unit) ?: return
        }
        applyDimension(edgeId, valueMm, MeasurementSource.MANUAL)
    }

    /**
     * Writes a decoded rangefinder reading into the dimension of [edgeId].
     *
     * The caller passes only the edge whose editor is open, so readings never land on a wall the
     * user is not looking at. Frames with an unknown unit are dropped, like in measurement lists.
     *
     * Returns `true` when the reading became the value of the wall, so the screen can close the
     * card: a measurement is complete when the device sends it, and an unchanged value still counts
     * because the wall already holds it.
     */
    fun onRangefinderFrame(edgeId: String, frame: DistanceFrame): Boolean {
        val valueMm = frame.toCanonicalMillimeters() ?: return false
        applyDimension(edgeId, valueMm, MeasurementSource.RANGFINDER)
        return true
    }

    /** Changes the unit of the open plan; the stored millimeters are not touched. */
    fun setUnit(unit: LengthUnit) {
        lastUnit.save(unit)
        val current = _openPlan.value ?: return
        if (current.unit == unit) return
        commit(current.copy(unit = unit, updatedAt = System.currentTimeMillis()))
    }

    /** Removes the dimension of [edgeId] and keeps the edge itself. */
    fun removeEdgeDimension(edgeId: String) {
        val current = _openPlan.value ?: return
        val updated = PlanEditing.removeDimension(current, edgeId)
        if (updated === current) return
        commit(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    fun clearDrawing() {
        val current = _openPlan.value ?: return
        if (!current.hasDrawing) return
        commit(
            PlanEditing.clearDrawing(current)
                .copy(updatedAt = System.currentTimeMillis()),
        )
    }

    private fun newId(): String = UUID.randomUUID().toString()

    /**
     * Writes a dimension unless the edge already carries this value.
     *
     * Closing a card without edits, or pointing the device at one wall repeatedly, must not rewrite
     * the dimension: every write is an undo step and a file update. The source is ignored on
     * purpose, otherwise merely dismissing a card would turn a rangefinder reading into a manual
     * one.
     */
    private fun applyDimension(
        edgeId: String,
        valueMm: String,
        source: MeasurementSource,
    ) {
        val current = _openPlan.value ?: return
        val edge = current.edges.firstOrNull { it.id == edgeId } ?: return
        val existing = edge.dimension
        if (existing != null && existing.valueMm == valueMm) return
        val now = System.currentTimeMillis()
        val updated = PlanEditing.setDimension(
            document = current,
            edgeId = edgeId,
            dimension = PlanDimension(
                valueMm = valueMm,
                source = source,
                updatedAt = now,
            ),
        )
        if (updated === current) return
        commit(updated.copy(updatedAt = now))
    }

    private fun commit(plan: PlanDocument, undoable: Boolean = true) {
        val previous = _openPlan.value
        if (undoable && previous != null && previous != plan) rememberUndo(previous)
        _openPlan.value = plan
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.update(plan) }
                    .onFailure { report(R.string.plan_error_save) }
            }
        }
    }

    private fun rememberUndo(document: PlanDocument) {
        undoHistory.addLast(document)
        while (undoHistory.size > UNDO_LIMIT) undoHistory.removeFirst()
        _canUndo.value = true
    }

    private fun resetUndo() {
        undoHistory.clear()
        _canUndo.value = false
    }

    private fun report(@StringRes id: Int) {
        _messages.tryEmit(getApplication<Application>().getString(id))
    }

    private companion object {
        const val DATA_DIRECTORY = "plans"
        const val UNDO_LIMIT = 50
    }
}
