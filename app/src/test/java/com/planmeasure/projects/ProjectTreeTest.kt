package com.planmeasure.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTreeTest {

    @Test
    fun visibleEntriesOrdersFoldersBeforeObjectsAndRespectsCollapse() {
        val folderA = ProjectFolder("a", "A")
        val folderB = ProjectFolder("b", "B", "a")
        val rootList = ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "root-list")
        val planA = ProjectObjectRef(ProjectObjectKind.PLAN, "plan-a", "a")
        val listB = ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "list-b", "b")
        val project = project(
            folders = listOf(folderA, folderB),
            objects = listOf(rootList, planA, listB),
        )
        val names = namesOf(
            ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "root-list") to "Root list",
            ProjectObjectKey(ProjectObjectKind.PLAN, "plan-a") to "Plan A",
            ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "list-b") to "List B",
        )

        assertEquals(
            listOf(
                ProjectTree.Entry.FolderEntry(folderA, 0),
                ProjectTree.Entry.FolderEntry(folderB, 1),
                ProjectTree.Entry.ObjectEntry(listB, 2),
                ProjectTree.Entry.ObjectEntry(planA, 1),
                ProjectTree.Entry.ObjectEntry(rootList, 0),
            ),
            ProjectTree.visibleEntries(project, emptySet(), names),
        )

        assertEquals(
            listOf(
                ProjectTree.Entry.FolderEntry(folderA, 0),
                ProjectTree.Entry.ObjectEntry(rootList, 0),
            ),
            ProjectTree.visibleEntries(project, setOf("a"), names),
        )
    }

    @Test
    fun visibleEntriesSkipsObjectsWithoutDocuments() {
        val present = ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "present")
        val missing = ProjectObjectRef(ProjectObjectKind.PLAN, "missing")
        val project = project(objects = listOf(present, missing))
        val names = namesOf(
            ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "present") to "Present",
        )

        val entries = ProjectTree.visibleEntries(project, emptySet(), names)

        assertEquals(listOf(ProjectTree.Entry.ObjectEntry(present, 0)), entries)
        assertEquals(1, ProjectTree.liveObjectCount(project, names))
    }

    @Test
    fun movesFolderAndRejectsCycles() {
        val project = project(
            folders = listOf(
                ProjectFolder("a", "A"),
                ProjectFolder("b", "B", "a"),
                ProjectFolder("c", "C", "b"),
                ProjectFolder("d", "D"),
            ),
        )

        assertFalse(ProjectTree.canMoveFolder(project, "a", "b"))
        assertFalse(ProjectTree.canMoveFolder(project, "a", "c"))
        assertFalse(ProjectTree.canMoveFolder(project, "a", "a"))
        assertTrue(ProjectTree.canMoveFolder(project, "b", null))
        assertTrue(ProjectTree.canMoveFolder(project, "a", "d"))

        val moved = ProjectTree.moveFolder(project, "b", null)

        assertEquals(
            null,
            moved.folders.first { it.id == "b" }.parentId,
        )
        assertSame(moved, ProjectTree.moveFolder(moved, "b", null))
    }

    @Test
    fun removesFolderSubtreeAndLiftsObjectsToParent() {
        val project = project(
            folders = listOf(
                ProjectFolder("a", "A"),
                ProjectFolder("b", "B", "a"),
                ProjectFolder("c", "C", "b"),
                ProjectFolder("d", "D"),
            ),
            objects = listOf(
                ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "in-a", "a"),
                ProjectObjectRef(ProjectObjectKind.PLAN, "in-b", "b"),
                ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "in-c", "c"),
                ProjectObjectRef(ProjectObjectKind.PLAN, "in-d", "d"),
                ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "root"),
            ),
        )

        val removed = ProjectTree.removeFolder(project, "a")

        assertEquals(listOf("d"), removed.folders.map { it.id })
        assertEquals(
            listOf(null, null, null, "d", null),
            removed.objects.map { it.folderId },
        )
    }

    @Test
    fun upsertObjectAddsAndMovesWithoutDuplicates() {
        val project = project(
            folders = listOf(
                ProjectFolder("a", "A"),
                ProjectFolder("b", "B"),
            ),
        )
        val inA = ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "a")

        val added = ProjectTree.upsertObject(project, inA)
        assertEquals(1, added.objects.size)
        assertEquals("a", added.objects.single().folderId)

        val inB = ProjectTree.upsertObject(
            added,
            ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "b"),
        )
        assertEquals(1, inB.objects.size)
        assertEquals("b", inB.objects.single().folderId)

        assertSame(inB, ProjectTree.upsertObject(inB, inB.objects.single()))
    }

    @Test
    fun folderChoicesExcludeTheMovedSubtree() {
        val project = project(
            folders = listOf(
                ProjectFolder("a", "A"),
                ProjectFolder("b", "B", "a"),
                ProjectFolder("c", "C", "b"),
                ProjectFolder("d", "D"),
            ),
        )

        assertEquals(
            listOf(
                ProjectTree.FolderChoice(null, null, 0),
                ProjectTree.FolderChoice("d", "D", 1),
            ),
            ProjectTree.folderChoices(project, excludeSubtreeOf = "a"),
        )
        assertEquals(
            listOf(
                ProjectTree.FolderChoice(null, null, 0),
                ProjectTree.FolderChoice("a", "A", 1),
                ProjectTree.FolderChoice("d", "D", 1),
            ),
            ProjectTree.folderChoices(project, excludeSubtreeOf = "b"),
        )
    }

    @Test
    fun folderPathIncludesTheFolderAndItsAncestors() {
        val project = project(
            folders = listOf(
                ProjectFolder("a", "A"),
                ProjectFolder("b", "B", "a"),
                ProjectFolder("c", "C", "b"),
            ),
        )

        assertEquals(setOf("c", "b", "a"), ProjectTree.folderPath(project, "c"))
        assertEquals(setOf("a"), ProjectTree.folderPath(project, "a"))
        assertEquals(emptySet<String>(), ProjectTree.folderPath(project, "missing"))
    }

    @Test
    fun projectObjectIdsContainOnlyTheRequestedKind() {
        val list = ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "list")
        val plan = ProjectObjectKey(ProjectObjectKind.PLAN, "plan")
        val projects = listOf(
            ProjectDocument(
                id = "first",
                name = "First",
                createdAt = 1,
                objects = listOf(
                    ProjectObjectRef(list.kind, list.refId),
                    ProjectObjectRef(plan.kind, plan.refId),
                ),
            ),
            ProjectDocument(
                id = "second",
                name = "Second",
                createdAt = 2,
                objects = listOf(
                    ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "list-2"),
                ),
            ),
        )

        assertEquals(
            setOf("list", "list-2"),
            ProjectTree.projectObjectIds(projects, ProjectObjectKind.MEASUREMENT_LIST),
        )
        assertEquals(
            setOf("plan"),
            ProjectTree.projectObjectIds(projects, ProjectObjectKind.PLAN),
        )
        assertEquals("First", ProjectTree.projectNameOf(projects, plan))
        assertEquals(
            null,
            ProjectTree.projectNameOf(
                projects,
                ProjectObjectKey(ProjectObjectKind.PLAN, "missing"),
            ),
        )
    }

    @Test
    fun attachesObjectToTargetAndRemovesItFromAnotherProject() {
        val target = ProjectDocument(
            id = "target",
            name = "Target",
            createdAt = 1,
            folders = listOf(ProjectFolder("folder", "Folder")),
        )
        val other = ProjectDocument(
            id = "other",
            name = "Other",
            createdAt = 2,
            objects = listOf(ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "folder")),
        )
        val ref = ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "folder")

        val changed = ProjectTree.attachObject(
            projects = listOf(target, other),
            target = target,
            ref = ref,
            updatedAt = 123L,
        )

        assertEquals(2, changed.size)
        val changedTarget = changed.first { it.id == "target" }
        assertEquals(123L, changedTarget.updatedAt)
        assertTrue(ProjectTree.containsObject(changedTarget, ref.key()))
        assertEquals("folder", changedTarget.objects.single().folderId)
        assertTrue(changed.first { it.id == "other" }.objects.isEmpty())
    }

    @Test
    fun attachingAnObjectThatIsAlreadyInPlaceChangesNothing() {
        val target = ProjectDocument(
            id = "target",
            name = "Target",
            createdAt = 1,
            folders = listOf(ProjectFolder("folder", "Folder")),
            objects = listOf(ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "folder")),
        )

        val changed = ProjectTree.attachObject(
            projects = listOf(target),
            target = target,
            ref = ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "folder"),
            updatedAt = 123L,
        )

        assertTrue(changed.isEmpty())
    }

    @Test
    fun attachObjectUsesTheOpenDocumentOverAStaleRepositoryCopy() {
        val stale = ProjectDocument(
            id = "project",
            name = "Project",
            createdAt = 1,
            objects = listOf(ProjectObjectRef(ProjectObjectKind.PLAN, "plan")),
        )
        val openDocument = stale.copy(
            folders = listOf(ProjectFolder("folder", "Folder")),
        )

        val changed = ProjectTree.attachObject(
            projects = listOf(stale),
            target = openDocument,
            ref = ProjectObjectRef(ProjectObjectKind.PLAN, "plan", "folder"),
            updatedAt = 123L,
        )

        val updated = changed.single()
        assertEquals("project", updated.id)
        assertEquals("folder", updated.objects.single().folderId)
    }

    @Test
    fun repairsReferencesToMissingFolders() {
        val project = project(
            objects = listOf(
                ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, "orphan", "missing"),
            ),
        )
        val names = namesOf(
            ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "orphan") to "Orphan",
        )

        val entries = ProjectTree.visibleEntries(project, emptySet(), names)

        assertEquals(
            listOf(ProjectTree.Entry.ObjectEntry(project.objects.single().copy(folderId = null), 0)),
            entries,
        )
    }

    @Test
    fun missingObjectKeysSkipsLiveAndUnreadableDocuments() {
        val live = ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "live")
        val unreadable = ProjectObjectKey(ProjectObjectKind.PLAN, "unreadable")
        val deleted = ProjectObjectKey(ProjectObjectKind.MEASUREMENT_LIST, "deleted")
        val document = project(
            objects = listOf(
                ProjectObjectRef(live.kind, live.refId),
                ProjectObjectRef(unreadable.kind, unreadable.refId),
                ProjectObjectRef(deleted.kind, deleted.refId),
                ProjectObjectRef(deleted.kind, deleted.refId),
            ),
        )

        val missing = ProjectTree.missingObjectKeys(
            project = document,
            liveKeys = setOf(live),
            hasDocument = { it == unreadable },
        )

        assertEquals(listOf(deleted), missing)
    }

    private fun project(
        folders: List<ProjectFolder> = emptyList(),
        objects: List<ProjectObjectRef> = emptyList(),
    ) = ProjectDocument(
        id = "project",
        name = "Project",
        createdAt = 1,
        folders = folders,
        objects = objects,
    )

    private fun namesOf(vararg pairs: Pair<ProjectObjectKey, String>): Map<ProjectObjectKey, String> =
        mapOf(*pairs)
}
