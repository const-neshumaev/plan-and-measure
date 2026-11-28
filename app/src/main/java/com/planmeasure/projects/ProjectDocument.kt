package com.planmeasure.projects

import kotlinx.serialization.Serializable

/** Current version of the on-disk project document. Bump when the shape changes. */
const val PROJECT_SCHEMA_VERSION = 1

/**
 * A folder of a project tree.
 *
 * Folders are stored flat with a parent reference and assembled by [ProjectTree]. This keeps
 * moving a folder a small immutable update instead of a recursive tree rewrite, and makes a cycle
 * check a pure function with tests.
 */
@Serializable
data class ProjectFolder(
    val id: String,
    val name: String,
    val parentId: String? = null,
)

/** Kind of the document a project reference points to. */
@Serializable
enum class ProjectObjectKind {
    MEASUREMENT_LIST,
    PLAN,
    SURVEY,
}

/**
 * A reference to a measurement list, a plan or a survey inside a project.
 *
 * The project keeps only the kind and the id: the name is resolved from the live repository, so
 * renaming an object updates every tree without touching the project file. A project never changes
 * the referenced document (ADR 0015).
 */
@Serializable
data class ProjectObjectRef(
    val kind: ProjectObjectKind,
    val refId: String,
    val folderId: String? = null,
)

/** Identity of a referenced document, independent of the folder it is placed in. */
data class ProjectObjectKey(
    val kind: ProjectObjectKind,
    val refId: String,
)

fun ProjectObjectRef.key(): ProjectObjectKey = ProjectObjectKey(kind, refId)

/**
 * One named project, stored as `projects/<id>.json`.
 *
 * A project is an organizational layer over independent documents: it owns a tree of folders and
 * references, but not the lists, plans and surveys themselves. Deleting a project therefore
 * deletes only the tree, and its objects become independent again (ADR 0015).
 */
@Serializable
data class ProjectDocument(
    val schemaVersion: Int = PROJECT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val folders: List<ProjectFolder> = emptyList(),
    val objects: List<ProjectObjectRef> = emptyList(),
)
