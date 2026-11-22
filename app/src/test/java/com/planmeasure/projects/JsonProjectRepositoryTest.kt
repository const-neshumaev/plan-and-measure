package com.planmeasure.projects

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonProjectRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temp.newFolder("projects")
    }

    private fun repository(
        clock: () -> Long = { 1_000L },
        idGenerator: () -> String = { PROJECT_ID },
    ) = JsonProjectRepository(directory, clock, idGenerator)

    private fun withTree(project: ProjectDocument, updatedAt: Long = 2_000L) = project.copy(
        updatedAt = updatedAt,
        folders = listOf(
            ProjectFolder(FOLDER_ID, "Living room"),
        ),
        objects = listOf(
            ProjectObjectRef(ProjectObjectKind.MEASUREMENT_LIST, LIST_ID, FOLDER_ID),
            ProjectObjectRef(ProjectObjectKind.PLAN, PLAN_ID),
        ),
    )

    @Test
    fun createsProjectAndReadsItBack() = runBlocking {
        val repository = repository()
        repository.reload()

        val created = repository.create("Flat")

        assertEquals("Flat", created.name)
        assertTrue(File(directory, "$PROJECT_ID.json").isFile)
        assertEquals(1, repository.projects.value.size)

        val reopened = repository()
        reopened.reload()
        assertEquals(created, reopened.load(PROJECT_ID))
    }

    @Test
    fun updatePersistsTree() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Flat")

        val updated = withTree(created)
        repository.update(updated)

        val summary = repository.projects.value.single()
        assertEquals(1, summary.folders.size)
        assertEquals(2, summary.objects.size)

        val reopened = repository()
        reopened.reload()
        assertEquals(updated, reopened.load(PROJECT_ID))
    }

    @Test
    fun updateAllWritesSeveralDocuments() = runBlocking {
        var next = 0
        val ids = listOf(FIRST_ID, SECOND_ID)
        val repository = JsonProjectRepository(
            directory = directory,
            clock = { 1_000L },
            idGenerator = { ids[next++] },
        )
        repository.reload()
        val first = repository.create("First")
        val second = repository.create("Second")

        val updatedFirst = first.copy(name = "First 2", updatedAt = 3_000L)
        val updatedSecond = second.copy(name = "Second 2", updatedAt = 4_000L)
        repository.updateAll(listOf(updatedFirst, updatedSecond))

        val reopened = repository()
        reopened.reload()
        assertEquals(updatedFirst, reopened.load(FIRST_ID))
        assertEquals(updatedSecond, reopened.load(SECOND_ID))
        assertEquals(SECOND_ID, reopened.projects.value.first().id)
    }

    @Test
    fun renamesAndDeletesProject() = runBlocking {
        val repository = repository()
        repository.reload()
        repository.create("Flat")

        val renamed = repository.rename(PROJECT_ID, "Hall")
        assertEquals("Hall", renamed?.name)
        assertEquals("Hall", repository.load(PROJECT_ID)?.name)

        repository.delete(PROJECT_ID)

        assertNull(repository.load(PROJECT_ID))
        assertTrue(repository.projects.value.isEmpty())
        assertFalse(File(directory, "$PROJECT_ID.json").exists())
    }

    @Test
    fun readsBackupWhenMainFileIsCorrupted() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Flat")
        repository.update(withTree(created))

        File(directory, "$PROJECT_ID.json").writeText("{broken")

        val loaded = repository.load(PROJECT_ID)
        assertEquals("Flat", loaded?.name)
        // The backup was written before the update, so it still has the empty tree.
        assertTrue(loaded?.folders.isNullOrEmpty())
        assertTrue(loaded?.objects.isNullOrEmpty())

        val reopened = repository()
        reopened.reload()
        assertEquals(1, reopened.projects.value.size)
        assertTrue(reopened.projects.value.single().folders.isEmpty())
    }

    @Test
    fun sortsProjectsByLastUpdate() = runBlocking {
        var now = 1_000L
        val ids = listOf(FIRST_ID, SECOND_ID)
        var next = 0
        val repository = JsonProjectRepository(
            directory = directory,
            clock = { now },
            idGenerator = { ids[next++] },
        )
        repository.reload()

        val first = repository.create("First")
        now = 2_000L
        repository.create("Second")

        assertEquals(SECOND_ID, repository.projects.value.first().id)

        now = 3_000L
        repository.update(first.copy(updatedAt = now))

        assertEquals(FIRST_ID, repository.projects.value.first().id)
    }

    @Test
    fun rejectsBlankName() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository().create("   ") }
        }
    }

    @Test
    fun rejectsMalformedId() = runBlocking {
        assertNull(repository().load("../outside"))
    }

    private companion object {
        const val PROJECT_ID = "00000000-0000-0000-0000-000000000001"
        const val FIRST_ID = "00000000-0000-0000-0000-000000000002"
        const val SECOND_ID = "00000000-0000-0000-0000-000000000003"
        const val FOLDER_ID = "00000000-0000-0000-0000-000000000010"
        const val LIST_ID = "00000000-0000-0000-0000-000000000020"
        const val PLAN_ID = "00000000-0000-0000-0000-000000000030"
    }
}
