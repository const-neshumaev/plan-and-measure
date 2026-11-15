package com.planmeasure.plans

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planmeasure.R
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
 */
class PlansViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = JsonPlanRepository(File(app.filesDir, DATA_DIRECTORY))

    private val persistence = Mutex()

    val plans = repository.plans

    private val _openPlan = MutableStateFlow<PlanDocument?>(null)
    val openPlan = _openPlan.asStateFlow()

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
                    _openPlan.value = document
                }
            }
        }
    }

    fun close() {
        _openPlan.value = null
    }

    fun createPlan(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.create(name) }
                    .onSuccess { _openPlan.value = it }
                    .onFailure { report(R.string.plan_error_create) }
            }
        }
    }

    fun renameOpenPlan(name: String) {
        val current = _openPlan.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit(current.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
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
        val updated = PlanEditing.undoLastSegment(current)
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

    private fun commit(plan: PlanDocument) {
        _openPlan.value = plan
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.update(plan) }
                    .onFailure { report(R.string.plan_error_save) }
            }
        }
    }

    private fun report(@StringRes id: Int) {
        _messages.tryEmit(getApplication<Application>().getString(id))
    }

    private companion object {
        const val DATA_DIRECTORY = "plans"
    }
}
