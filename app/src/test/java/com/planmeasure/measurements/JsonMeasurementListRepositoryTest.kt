package com.planmeasure.measurements

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

class JsonMeasurementListRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temp.newFolder("measurements")
    }

    private fun repository(
        clock: () -> Long = { 1_000L },
        idGenerator: () -> String = { LIST_ID },
    ) = JsonMeasurementListRepository(directory, clock, idGenerator)

    private fun measurement() = Measurement(
        id = MEASUREMENT_ID,
        valueMm = "1763",
        comment = "Window",
        createdAt = 1_500L,
        source = MeasurementSource.MANUAL,
    )

    @Test
    fun createsListAndReadsItBack() = runBlocking {
        val repository = repository()
        repository.reload()

        val created = repository.create("Living room")

        assertEquals("Living room", created.name)
        assertTrue(File(directory, "$LIST_ID.json").isFile)
        assertEquals(1, repository.lists.value.size)
        assertEquals(0, repository.lists.value.single().measurementCount)

        val reopened = repository()
        reopened.reload()
        assertEquals(created, reopened.load(LIST_ID))
    }

    @Test
    fun updatePersistsMeasurements() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Living room")

        val updated = created.copy(updatedAt = 2_000L, measurements = listOf(measurement()))
        repository.update(updated)

        assertEquals(1, repository.lists.value.single().measurementCount)

        val reopened = repository()
        reopened.reload()
        assertEquals(updated, reopened.load(LIST_ID))
    }

    @Test
    fun renamesAndDeletesList() = runBlocking {
        val repository = repository()
        repository.reload()
        repository.create("Living room")

        val renamed = repository.rename(LIST_ID, "Hall")
        assertEquals("Hall", renamed?.name)
        assertEquals("Hall", repository.load(LIST_ID)?.name)

        repository.delete(LIST_ID)

        assertNull(repository.load(LIST_ID))
        assertTrue(repository.lists.value.isEmpty())
        assertFalse(File(directory, "$LIST_ID.json").exists())
    }

    @Test
    fun readsBackupWhenMainFileIsCorrupted() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Living room")
        repository.update(created.copy(updatedAt = 2_000L, measurements = listOf(measurement())))

        File(directory, "$LIST_ID.json").writeText("{broken")

        val loaded = repository.load(LIST_ID)
        assertEquals("Living room", loaded?.name)
        // The backup was written before the update, so it still has the empty list.
        assertTrue(loaded!!.measurements.isEmpty())

        val reopened = repository()
        reopened.reload()
        assertEquals(1, reopened.lists.value.size)
        assertEquals(0, reopened.lists.value.single().measurementCount)
    }

    @Test
    fun sortsListsByLastUpdate() = runBlocking {
        var now = 1_000L
        val ids = listOf(FIRST_ID, SECOND_ID)
        var next = 0
        val repository = JsonMeasurementListRepository(
            directory = directory,
            clock = { now },
            idGenerator = { ids[next++] },
        )
        repository.reload()

        val first = repository.create("First")
        now = 2_000L
        repository.create("Second")

        assertEquals(SECOND_ID, repository.lists.value.first().id)

        now = 3_000L
        repository.update(first.copy(updatedAt = now))

        assertEquals(FIRST_ID, repository.lists.value.first().id)
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
        const val LIST_ID = "00000000-0000-0000-0000-000000000001"
        const val FIRST_ID = "00000000-0000-0000-0000-000000000002"
        const val SECOND_ID = "00000000-0000-0000-0000-000000000003"
        const val MEASUREMENT_ID = "00000000-0000-0000-0000-000000000010"
    }
}
