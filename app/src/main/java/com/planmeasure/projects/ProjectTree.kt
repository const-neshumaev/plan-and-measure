package com.planmeasure.projects

/**
 * Pure tree operations for a project document.
 *
 * Projects never rewrite measurement lists or plans; they only keep references. The tree is stored
 * flat (folders with `parentId`, references with `folderId`) and assembled here, so moving a folder
 * or an object is an immutable update with an explicit cycle check.
 */
object ProjectTree {

    /** One visible row of the tree. */
    sealed interface Entry {
        val depth: Int

        data class FolderEntry(val folder: ProjectFolder, override val depth: Int) : Entry

        data class ObjectEntry(val ref: ProjectObjectRef, override val depth: Int) : Entry
    }

    /** One destination of a move dialog; [folderId] is `null` for the project root. */
    data class FolderChoice(val folderId: String?, val name: String?, val depth: Int)

    /**
     * Rows of the tree in display order: folders before objects on every level, both by name.
     *
     * Objects whose documents no longer exist are skipped: names come from the live repositories,
     * so a project cannot show a name for a deleted list or plan. The reference itself is kept in
     * the document and is not removed behind the user's back.
     */
    fun visibleEntries(
        project: ProjectDocument,
        collapsedFolderIds: Set<String>,
        objectNames: Map<ProjectObjectKey, String>,
    ): List<Entry> {
        val normalized = normalized(project)
        val foldersByParent = normalized.folders.groupBy { it.parentId }
        val refsByFolder = normalized.objects
            .filter { objectNames.containsKey(it.key()) }
            .distinctBy { it.key() }
            .groupBy { it.folderId }
        val visited = mutableSetOf<String>()
        val entries = mutableListOf<Entry>()

        fun walk(parentId: String?, depth: Int) {
            foldersByParent[parentId].orEmpty()
                .sortedBy { it.name.lowercase() }
                .forEach { folder ->
                    if (!visited.add(folder.id)) return@forEach
                    entries += Entry.FolderEntry(folder, depth)
                    if (folder.id !in collapsedFolderIds) walk(folder.id, depth + 1)
                }
            refsByFolder[parentId].orEmpty()
                .sortedBy { objectNames[it.key()].orEmpty().lowercase() }
                .forEach { entries += Entry.ObjectEntry(it, depth) }
        }

        walk(null, 0)
        return entries
    }

    /**
     * References whose documents are gone for good, in document order without duplicates.
     *
     * [liveKeys] are objects that loaded successfully; [hasDocument] reports whether any file
     * version for a key is still on disk. A key that is not live but still has a file is treated
     * as unreadable, not deleted, and is not returned: a temporary read failure must not erase
     * the reference (ADR 0017).
     */
    fun missingObjectKeys(
        project: ProjectDocument,
        liveKeys: Set<ProjectObjectKey>,
        hasDocument: (ProjectObjectKey) -> Boolean,
    ): List<ProjectObjectKey> = project.objects
        .map { it.key() }
        .distinct()
        .filter { key -> key !in liveKeys && !hasDocument(key) }

    /** Number of references whose documents still exist. */
    fun liveObjectCount(
        project: ProjectDocument,
        objectNames: Map<ProjectObjectKey, String>,
    ): Int = project.objects
        .map { it.key() }
        .distinct()
        .count { objectNames.containsKey(it) }

    /** Ids of all folders below [folderId], not including the folder itself. */
    fun descendantFolderIds(project: ProjectDocument, folderId: String): Set<String> {
        val children = project.folders.groupBy { it.parentId }
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.addLast(folderId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            children[id].orEmpty().forEach { child ->
                if (result.add(child.id)) queue.addLast(child.id)
            }
        }
        return result
    }

    /**
     * [folderId] together with all its ancestors, used to reveal a target folder after a move.
     * Returns an empty set when the folder is not part of the document.
     */
    fun folderPath(project: ProjectDocument, folderId: String): Set<String> {
        val parents = project.folders.associate { it.id to it.parentId }
        if (folderId !in parents) return emptySet()
        val path = mutableSetOf<String>()
        var current: String? = folderId
        while (current != null && path.add(current)) {
            current = parents[current]
        }
        return path
    }

    /** Whether [folderId] may become a child of [newParentId] without creating a cycle. */
    fun canMoveFolder(
        project: ProjectDocument,
        folderId: String,
        newParentId: String?,
    ): Boolean {
        if (project.folders.none { it.id == folderId }) return false
        if (newParentId != null && project.folders.none { it.id == newParentId }) return false
        if (newParentId == folderId) return false
        return newParentId == null || newParentId !in descendantFolderIds(project, folderId)
    }

    fun moveFolder(
        project: ProjectDocument,
        folderId: String,
        newParentId: String?,
    ): ProjectDocument {
        if (!canMoveFolder(project, folderId, newParentId)) return project
        val folder = project.folders.first { it.id == folderId }
        if (folder.parentId == newParentId) return project
        return project.copy(
            folders = project.folders.map {
                if (it.id == folderId) it.copy(parentId = newParentId) else it
            },
        )
    }

    /**
     * Removes a folder together with its subfolders.
     *
     * Objects inside the removed subtree are lifted to the removed folder's parent, so deleting
     * structure never deletes measurement lists or plans (ADR 0015).
     */
    fun removeFolder(project: ProjectDocument, folderId: String): ProjectDocument {
        val folder = project.folders.firstOrNull { it.id == folderId } ?: return project
        val removed = descendantFolderIds(project, folderId) + folderId
        val objects = project.objects.map { ref ->
            if (ref.folderId in removed) ref.copy(folderId = folder.parentId) else ref
        }
        return project.copy(
            folders = project.folders.filterNot { it.id in removed },
            objects = objects,
        )
    }

    fun addFolder(project: ProjectDocument, folder: ProjectFolder): ProjectDocument {
        if (project.folders.any { it.id == folder.id }) return project
        val parentId = folder.parentId?.takeIf { id -> project.folders.any { it.id == id } }
        return project.copy(folders = project.folders + folder.copy(parentId = parentId))
    }

    fun renameFolder(
        project: ProjectDocument,
        folderId: String,
        name: String,
    ): ProjectDocument {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return project
        val folder = project.folders.firstOrNull { it.id == folderId } ?: return project
        if (folder.name == trimmed) return project
        return project.copy(
            folders = project.folders.map {
                if (it.id == folderId) it.copy(name = trimmed) else it
            },
        )
    }

    /**
     * Adds a reference or moves an existing one to [ProjectObjectRef.folderId].
     *
     * A reference is unique by kind and id, so adding an object that is already in the project
     * changes its folder instead of creating a duplicate row.
     */
    fun upsertObject(project: ProjectDocument, ref: ProjectObjectRef): ProjectDocument {
        val folderId = ref.folderId?.takeIf { id -> project.folders.any { it.id == id } }
        val normalizedRef = ref.copy(folderId = folderId)
        val existing = project.objects.firstOrNull { it.key() == normalizedRef.key() }
        if (existing == normalizedRef) return project
        val objects = if (existing == null) {
            project.objects + normalizedRef
        } else {
            project.objects.map {
                if (it.key() == normalizedRef.key()) normalizedRef else it
            }
        }
        return project.copy(objects = objects)
    }

    fun removeObject(project: ProjectDocument, key: ProjectObjectKey): ProjectDocument {
        if (project.objects.none { it.key() == key }) return project
        return project.copy(objects = project.objects.filterNot { it.key() == key })
    }

    /**
     * Attaches [ref] to [target] and removes the same object from every other project.
     *
     * The open [target] document is used as is, even if the repository still holds a copy with the
     * same id: the screen may already show a folder that the repository copy does not have yet,
     * and normalizing the folder against the stale copy would silently move the object to the root.
     * Returns only the documents that changed, with [updatedAt] applied.
     */
    fun attachObject(
        projects: List<ProjectDocument>,
        target: ProjectDocument,
        ref: ProjectObjectRef,
        updatedAt: Long,
    ): List<ProjectDocument> {
        val key = ref.key()
        val changed = mutableListOf<ProjectDocument>()

        val updatedTarget = upsertObject(target, ref)
        if (updatedTarget != target) {
            changed += updatedTarget.copy(updatedAt = updatedAt)
        }

        projects.forEach { project ->
            if (project.id == target.id) return@forEach
            if (containsObject(project, key)) {
                changed += removeObject(project, key).copy(updatedAt = updatedAt)
            }
        }
        return changed
    }

    fun containsObject(project: ProjectDocument, key: ProjectObjectKey): Boolean =
        project.objects.any { it.key() == key }

    /** Name of the project that contains [key], or `null` for an independent object. */
    fun projectNameOf(projects: List<ProjectDocument>, key: ProjectObjectKey): String? =
        projects.firstOrNull { project -> project.objects.any { it.key() == key } }?.name

    /** Object id to project name for one kind, used for the badge in the objects' own sections. */
    fun projectNames(
        projects: List<ProjectDocument>,
        kind: ProjectObjectKind,
    ): Map<String, String> = buildMap {
        projects.forEach { project ->
            project.objects.forEach { ref ->
                if (ref.kind == kind) put(ref.refId, project.name)
            }
        }
    }

    /** Destinations for a move dialog, in tree order. The root is always the first choice. */
    fun folderChoices(
        project: ProjectDocument,
        excludeSubtreeOf: String? = null,
    ): List<FolderChoice> {
        val normalized = normalized(project)
        val excluded = excludeSubtreeOf
            ?.let { descendantFolderIds(normalized, it) + it }
            .orEmpty()
        val foldersByParent = normalized.folders.groupBy { it.parentId }
        val visited = mutableSetOf<String>()
        val choices = mutableListOf(FolderChoice(null, null, 0))

        fun walk(parentId: String?, depth: Int) {
            foldersByParent[parentId].orEmpty()
                .sortedBy { it.name.lowercase() }
                .forEach { folder ->
                    if (!visited.add(folder.id) || folder.id in excluded) return@forEach
                    choices += FolderChoice(folder.id, folder.name, depth)
                    walk(folder.id, depth + 1)
                }
        }

        walk(null, 1)
        return choices
    }

    /**
     * Repairs references that point to a missing folder by moving them to the root. Corrupted
     * documents stay readable instead of losing rows silently.
     */
    private fun normalized(project: ProjectDocument): ProjectDocument {
        val folderIds = project.folders.mapTo(mutableSetOf()) { it.id }
        val folders = project.folders.map { folder ->
            val parentId = folder.parentId
            if (parentId != null && parentId !in folderIds) {
                folder.copy(parentId = null)
            } else {
                folder
            }
        }
        val objects = project.objects.map { ref ->
            val folderId = ref.folderId
            if (folderId != null && folderId !in folderIds) {
                ref.copy(folderId = null)
            } else {
                ref
            }
        }
        return if (folders == project.folders && objects == project.objects) {
            project
        } else {
            project.copy(folders = folders, objects = objects)
        }
    }
}
