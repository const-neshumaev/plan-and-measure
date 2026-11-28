package com.planmeasure.plans

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

class JsonPlanRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temp.newFolder("plans")
    }

    private fun repository(
        clock: () -> Long = { 1_000L },
        idGenerator: () -> String = { PLAN_ID },
    ) = JsonPlanRepository(directory, clock, idGenerator)

    private fun withDrawing(plan: PlanDocument, updatedAt: Long = 2_000L) = plan.copy(
        updatedAt = updatedAt,
        nodes = listOf(
            PlanNode(NODE_A, 2.0, 2.0),
            PlanNode(NODE_B, 8.0, 2.0),
        ),
        contours = listOf(
            PlanContour(CONTOUR_ID, listOf(PlanEdge(EDGE_ID, NODE_A, NODE_B))),
        ),
    )

    @Test
    fun createsPlanAndReadsItBack() = runBlocking {
        val repository = repository()
        repository.reload()

        val created = repository.create("Living room")

        assertEquals("Living room", created.name)
        assertTrue(File(directory, "$PLAN_ID.json").isFile)
        assertTrue(repository.hasDocument(PLAN_ID))
        assertFalse(repository.hasDocument("not-a-uuid"))
        assertEquals(1, repository.plans.value.size)

        val reopened = repository()
        reopened.reload()
        assertEquals(created, reopened.load(PLAN_ID))
    }

    @Test
    fun hasDocumentStillSeesTheBackupWhenTheMainFileIsGone() = runBlocking {
        val repository = repository()
        repository.reload()

        repository.create("Living room")
        val created = repository.load(PLAN_ID)!!
        repository.update(created.copy(name = "Hall"))
        assertTrue(File(directory, "$PLAN_ID.json.bak").isFile)

        File(directory, "$PLAN_ID.json").delete()

        assertTrue(repository.hasDocument(PLAN_ID))
    }

    @Test
    fun updatePersistsDrawing() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Living room")

        val updated = withDrawing(created)
        repository.update(updated)

        val listEntry = repository.plans.value.single()
        assertEquals(1, listEntry.edgeCount)

        val reopened = repository()
        reopened.reload()
        val loaded = reopened.load(PLAN_ID)
        assertEquals(updated, loaded)
        assertEquals(1, loaded?.edgeCount)
    }

    @Test
    fun renamesAndDeletesPlan() = runBlocking {
        val repository = repository()
        repository.reload()
        repository.create("Living room")

        val renamed = repository.rename(PLAN_ID, "Hall")
        assertEquals("Hall", renamed?.name)
        assertEquals("Hall", repository.load(PLAN_ID)?.name)

        repository.delete(PLAN_ID)

        assertNull(repository.load(PLAN_ID))
        assertTrue(repository.plans.value.isEmpty())
        assertFalse(File(directory, "$PLAN_ID.json").exists())
        assertFalse(repository.hasDocument(PLAN_ID))
    }

    @Test
    fun readsBackupWhenMainFileIsCorrupted() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Living room")
        repository.update(withDrawing(created))

        File(directory, "$PLAN_ID.json").writeText("{broken")

        val loaded = repository.load(PLAN_ID)
        assertEquals("Living room", loaded?.name)
        // The backup was written before the update, so it still has an empty drawing.
        assertEquals(0, loaded?.edgeCount)

        val reopened = repository()
        reopened.reload()
        assertEquals(1, reopened.plans.value.size)
        assertFalse(reopened.plans.value.single().hasDrawing)
    }

    @Test
    fun sortsPlansByLastUpdate() = runBlocking {
        var now = 1_000L
        val ids = listOf(FIRST_ID, SECOND_ID)
        var next = 0
        val repository = JsonPlanRepository(
            directory = directory,
            clock = { now },
            idGenerator = { ids[next++] },
        )
        repository.reload()

        val first = repository.create("First")
        now = 2_000L
        repository.create("Second")

        assertEquals(SECOND_ID, repository.plans.value.first().id)

        now = 3_000L
        repository.update(first.copy(updatedAt = now))

        assertEquals(FIRST_ID, repository.plans.value.first().id)
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
        const val PLAN_ID = "00000000-0000-0000-0000-000000000001"
        const val FIRST_ID = "00000000-0000-0000-0000-000000000002"
        const val SECOND_ID = "00000000-0000-0000-0000-000000000003"
        const val NODE_A = "00000000-0000-0000-0000-000000000010"
        const val NODE_B = "00000000-0000-0000-0000-000000000011"
        const val EDGE_ID = "00000000-0000-0000-0000-000000000020"
        const val CONTOUR_ID = "00000000-0000-0000-0000-000000000030"
    }
}
