package com.planmeasure.ui.projects

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import com.planmeasure.projects.ProjectDocument
import com.planmeasure.projects.ProjectTree

/** One tree row that the user picked up. */
internal data class DraggedTreeRow(
    val entry: ProjectTree.Entry,
    val name: String,
    val index: Int,
)

/** Where a dragged row lands; `null` folder means the project root. */
internal data class TreeDropTarget(
    val folderId: String?,
    val rowIndex: Int?,
    val validity: TreeDropValidity,
)

internal enum class TreeDropValidity {
    /** The move changes the tree. */
    Valid,

    /** The row is already there; nothing to confirm or write. */
    NoChange,

    /** A folder cannot be moved into itself or a descendant. */
    Invalid,
}

/** Border state of one visible row while a drag is in progress. */
internal enum class DropHighlight {
    None,
    Target,
    Invalid,
}

/** The move the user agreed to after releasing a dragged row. */
internal data class PendingTreeMove(
    val entry: ProjectTree.Entry,
    val name: String,
    val folderId: String?,
)

/** Folder that receives a row when it is dropped on [entry], or `null` for the project root. */
internal fun destinationFolderIdFor(entry: ProjectTree.Entry): String? = when (entry) {
    is ProjectTree.Entry.FolderEntry -> entry.folder.id
    is ProjectTree.Entry.ObjectEntry -> entry.ref.folderId
}

/**
 * Checks a drop without changing anything.
 *
 * A folder may not be moved into itself or into its own subtree, because that would detach the
 * subtree from the tree. Dropping a row back into its current folder changes nothing and must not
 * ask for confirmation (ADR 0018).
 */
internal fun treeDropValidity(
    project: ProjectDocument,
    dragged: ProjectTree.Entry,
    destinationFolderId: String?,
): TreeDropValidity = when (dragged) {
    is ProjectTree.Entry.FolderEntry -> when {
        destinationFolderId == dragged.folder.parentId -> TreeDropValidity.NoChange
        destinationFolderId == dragged.folder.id -> TreeDropValidity.Invalid
        destinationFolderId != null &&
            ProjectTree.descendantFolderIds(project, dragged.folder.id)
                .contains(destinationFolderId) -> TreeDropValidity.Invalid

        else -> TreeDropValidity.Valid
    }

    is ProjectTree.Entry.ObjectEntry ->
        if (destinationFolderId == dragged.ref.folderId) {
            TreeDropValidity.NoChange
        } else {
            TreeDropValidity.Valid
        }
}

/**
 * Index of the visible row under [boxY], where coordinates are relative to the list container.
 *
 * Item offsets in [LazyListLayoutInfo] are relative to the viewport start, which sits at
 * [LazyListLayoutInfo.viewportStartOffset] inside the container.
 */
internal fun rowIndexAt(boxY: Float, layoutInfo: LazyListLayoutInfo): Int? {
    val viewportY = boxY + layoutInfo.viewportStartOffset
    return layoutInfo.visibleItemsInfo
        .firstOrNull { viewportY >= it.offset && viewportY < it.offset + it.size }
        ?.index
}
