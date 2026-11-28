package com.planmeasure.projects

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
 * Owns saved projects and the project tree that is currently open.
 *
 * A project is an organizational layer: attaching, moving and removing objects changes only
 * project documents. Measurement lists, plans and surveys are owned by their own ViewModels and
 * are never rewritten here (ADR 0015). Moving an object to another project is one repository operation over
 * both documents, so an object never stays in two projects.
 */
class ProjectsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = JsonProjectRepository(File(app.filesDir, DATA_DIRECTORY))

    private val persistence = Mutex()

    val projects = repository.projects

    private val _openProject = MutableStateFlow<ProjectDocument?>(null)
    val openProject = _openProject.asStateFlow()

    /**
     * Object of the open project that the user is editing, or `null` when the tree is shown.
     *
     * Lives in the ViewModel rather than the screen so that opening the connection screen
     * temporarily does not lose the object the user came from.
     */
    private val _openObject = MutableStateFlow<ProjectObjectKey?>(null)
    val openObject = _openObject.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.reload() }
                .onFailure { report(R.string.project_error_load) }
            _loading.value = false
        }
    }

    fun open(id: String) {
        viewModelScope.launch {
            persistence.withLock {
                val document = runCatching { repository.load(id) }.getOrNull()
                if (document == null) {
                    report(R.string.project_error_load)
                } else {
                    _openObject.value = null
                    _openProject.value = document
                }
            }
        }
    }

    fun close() {
        _openObject.value = null
        _openProject.value = null
    }

    /** Opens an object of the project; the tree stays in the ViewModel for the back path. */
    fun selectObject(key: ProjectObjectKey) {
        _openObject.value = key
    }

    fun closeObject() {
        _openObject.value = null
    }

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.create(name) }
                    .onSuccess {
                        _openObject.value = null
                        _openProject.value = it
                    }
                    .onFailure { report(R.string.project_error_create) }
            }
        }
    }

    fun renameProject(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            persistence.withLock {
                val renamed = runCatching { repository.rename(id, name) }.getOrNull()
                when {
                    renamed == null -> report(R.string.project_error_save)
                    _openProject.value?.id == id -> _openProject.value = renamed
                }
            }
        }
    }

    fun deleteProject(id: String) {
        if (_openProject.value?.id == id) close()
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.delete(id) }
                    .onFailure { report(R.string.project_error_delete) }
            }
        }
    }

    fun addFolder(parentId: String?, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit { project ->
            ProjectTree.addFolder(
                project,
                ProjectFolder(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    parentId = parentId,
                ),
            )
        }
    }

    fun renameFolder(folderId: String, name: String) {
        commit { project -> ProjectTree.renameFolder(project, folderId, name) }
    }

    fun deleteFolder(folderId: String) {
        commit { project -> ProjectTree.removeFolder(project, folderId) }
    }

    fun moveFolder(folderId: String, newParentId: String?) {
        commit { project -> ProjectTree.moveFolder(project, folderId, newParentId) }
    }

    fun removeObject(kind: ProjectObjectKind, refId: String) {
        val key = ProjectObjectKey(kind, refId)
        commit { project -> ProjectTree.removeObject(project, key) }
    }

    /** Removes [keys] from the open project only; used by the manual cleanup action. */
    fun removeObjects(keys: Collection<ProjectObjectKey>) {
        if (keys.isEmpty()) return
        commit { project -> keys.fold(project) { acc, key -> ProjectTree.removeObject(acc, key) } }
    }

    /**
     * Drops a reference to an object that has already been deleted from every project.
     *
     * The object document is removed before this runs, so a failure here leaves a hidden stale
     * reference that the manual cleanup can remove later (ADR 0017). The open project is taken
     * from memory when `_openProject` is set, so an in-flight edit is not overwritten by a stale
     * repository copy.
     */
    fun removeReferenceEverywhere(kind: ProjectObjectKind, refId: String) {
        val key = ProjectObjectKey(kind, refId)
        viewModelScope.launch {
            persistence.withLock {
                val open = _openProject.value
                val repositoryProjects = repository.projects.value
                val merged = repositoryProjects.map { project ->
                    if (open != null && project.id == open.id) open else project
                }.let { projects ->
                    if (open != null && projects.none { it.id == open.id }) projects + open else projects
                }
                val now = System.currentTimeMillis()
                val changed = merged.mapNotNull { project ->
                    val removed = ProjectTree.removeObject(project, key)
                    if (removed == project) null else removed.copy(updatedAt = now)
                }
                if (changed.isEmpty()) return@withLock

                runCatching { repository.updateAll(changed) }
                    .onSuccess {
                        changed.firstOrNull { it.id == open?.id }?.let { _openProject.value = it }
                    }
                    .onFailure { report(R.string.project_error_save) }
            }
        }
    }

    /**
     * Attaches an object to the open project or moves it inside the tree.
     *
     * If the object already belongs to another project, its reference is removed there in the same
     * repository operation. The object document itself is not touched.
     */
    fun attachObject(kind: ProjectObjectKind, refId: String, folderId: String?) {
        val ref = ProjectObjectRef(kind = kind, refId = refId, folderId = folderId)
        viewModelScope.launch {
            persistence.withLock {
                val current = _openProject.value ?: return@withLock
                val now = System.currentTimeMillis()
                val changed = ProjectTree.attachObject(
                    projects = repository.projects.value,
                    target = current,
                    ref = ref,
                    updatedAt = now,
                )
                if (changed.isEmpty()) return@withLock

                runCatching { repository.updateAll(changed) }
                    .onSuccess {
                        changed.firstOrNull { it.id == current.id }
                            ?.let { _openProject.value = it }
                    }
                    .onFailure { report(R.string.project_error_save) }
            }
        }
    }

    private fun commit(transform: (ProjectDocument) -> ProjectDocument) {
        val current = _openProject.value ?: return
        val updated = transform(current)
        if (updated == current) return
        val touched = updated.copy(updatedAt = System.currentTimeMillis())
        _openProject.value = touched
        viewModelScope.launch {
            persistence.withLock {
                runCatching { repository.update(touched) }
                    .onFailure { report(R.string.project_error_save) }
            }
        }
    }

    private fun report(@StringRes id: Int) {
        _messages.tryEmit(getApplication<Application>().getString(id))
    }

    private companion object {
        const val DATA_DIRECTORY = "projects"
    }
}
