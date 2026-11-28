package com.planmeasure.ui.projects

import com.planmeasure.projects.ProjectDocument
import com.planmeasure.projects.ProjectFolder
import com.planmeasure.projects.ProjectObjectKind
import com.planmeasure.projects.ProjectObjectRef
import com.planmeasure.projects.ProjectTree
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTreeDragTest {

    private val project = ProjectDocument(
        id = "project",
        name = "Project",
        createdAt = 1,
        folders = listOf(
            ProjectFolder("root-folder", "Root folder"),
            ProjectFolder("child-folder", "Child folder", "root-folder"),
            ProjectFolder("grandchild", "Grandchild", "child-folder"),
        ),
    )

    private val folder = ProjectTree.Entry.FolderEntry(
        folder = project.folders.first { it.id == "root-folder" },
        depth = 0,
    )
    private val childFolder = ProjectTree.Entry.FolderEntry(
        folder = project.folders.first { it.id == "child-folder" },
        depth = 1,
    )
    private val grandchild = ProjectTree.Entry.FolderEntry(
        folder = project.folders.first { it.id == "grandchild" },
        depth = 2,
    )

    @Test
    fun destinationOfAnEntryIsItsFolder() {
        val nested = ProjectTree.Entry.ObjectEntry(
            ref = ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "child-folder"),
            depth = 2,
        )
        val rootObject = ProjectTree.Entry.ObjectEntry(
            ref = ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "list"),
            depth = 0,
        )

        assertEquals("child-folder", destinationFolderIdFor(nested))
        assertEquals(null, destinationFolderIdFor(rootObject))
        assertEquals("child-folder", destinationFolderIdFor(childFolder))
    }

    @Test
    fun droppingAFolderIntoItselfOrADescendantIsInvalid() {
        assertEquals(
            TreeDropValidity.Invalid,
            treeDropValidity(project, folder, "root-folder"),
        )
        assertEquals(
            TreeDropValidity.Invalid,
            treeDropValidity(project, folder, "child-folder"),
        )
        assertEquals(
            TreeDropValidity.Invalid,
            treeDropValidity(project, folder, "grandchild"),
        )
    }

    @Test
    fun droppingAFolderIntoItsCurrentParentChangesNothing() {
        assertEquals(
            TreeDropValidity.NoChange,
            treeDropValidity(project, childFolder, "root-folder"),
        )
        assertEquals(
            TreeDropValidity.NoChange,
            treeDropValidity(project, folder, null),
        )
    }

    @Test
    fun movingAFolderElsewhereIsValid() {
        assertEquals(
            TreeDropValidity.Valid,
            treeDropValidity(project, childFolder, null),
        )
        assertEquals(
            TreeDropValidity.Valid,
            treeDropValidity(project, grandchild, "root-folder"),
        )
    }

    @Test
    fun objectMoveIsPointlessOnlyInsideItsOwnFolder() {
        val entry = ProjectTree.Entry.ObjectEntry(
            ref = ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "list", "child-folder"),
            depth = 2,
        )

        assertEquals(TreeDropValidity.NoChange, treeDropValidity(project, entry, "child-folder"))
        assertEquals(TreeDropValidity.Valid, treeDropValidity(project, entry, null))
        assertEquals(TreeDropValidity.Valid, treeDropValidity(project, entry, "root-folder"))
    }
}
