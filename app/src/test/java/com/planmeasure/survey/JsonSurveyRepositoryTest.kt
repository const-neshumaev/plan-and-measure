package com.planmeasure.survey

import com.planmeasure.measurements.MeasurementSource
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

class JsonSurveyRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var photoDirectory: File

    @Before
    fun setUp() {
        directory = temp.newFolder("surveys")
        photoDirectory = temp.newFolder("photos")
    }

    private fun repository(
        clock: () -> Long = { 1_000L },
        idGenerator: () -> String = { SURVEY_ID },
    ) = JsonSurveyRepository(directory, photoDirectory, clock, idGenerator)

    private fun withSketch(survey: SurveyDocument, updatedAt: Long = 2_000L) = survey.copy(
        updatedAt = updatedAt,
        photo = SurveyPhoto(width = 2048, height = 1536),
        segments = listOf(
            SurveySegment(
                id = SEGMENT_ID,
                start = SurveyPoint(10.0, 10.0),
                end = SurveyPoint(300.0, 10.0),
                dimension = SurveyDimension("3200", MeasurementSource.MANUAL, 1_500L),
            ),
        ),
    )

    @Test
    fun createsSurveyAndReadsItBack() = runBlocking {
        val repository = repository()
        repository.reload()

        val created = repository.create("Living room")

        assertEquals("Living room", created.name)
        assertTrue(File(directory, "$SURVEY_ID.json").isFile)
        assertTrue(repository.hasDocument(SURVEY_ID))
        assertFalse(repository.hasDocument("not-a-uuid"))
        assertEquals(1, repository.surveys.value.size)

        val reopened = repository()
        reopened.reload()
        assertEquals(created, reopened.load(SURVEY_ID))
    }

    @Test
    fun updatePersistsSketch() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Living room")

        val updated = withSketch(created)
        repository.update(updated)

        assertEquals(1, repository.surveys.value.single().segmentCount)

        val reopened = repository()
        reopened.reload()
        assertEquals(updated, reopened.load(SURVEY_ID))
    }

    @Test
    fun renamesAndDeletesSurveyWithItsPhoto() = runBlocking {
        val repository = repository()
        repository.reload()
        repository.create("Living room")
        File(photoDirectory, "$SURVEY_ID.jpg").writeText("photo")

        val renamed = repository.rename(SURVEY_ID, "Hall")
        assertEquals("Hall", renamed?.name)

        repository.delete(SURVEY_ID)

        assertNull(repository.load(SURVEY_ID))
        assertTrue(repository.surveys.value.isEmpty())
        assertFalse(File(directory, "$SURVEY_ID.json").exists())
        assertFalse(File(photoDirectory, "$SURVEY_ID.jpg").exists())
        assertFalse(repository.hasDocument(SURVEY_ID))
    }

    @Test
    fun readsBackupWhenMainFileIsCorrupted() = runBlocking {
        val repository = repository()
        repository.reload()
        val created = repository.create("Living room")
        repository.update(withSketch(created))

        File(directory, "$SURVEY_ID.json").writeText("{broken")

        val loaded = repository.load(SURVEY_ID)
        assertEquals("Living room", loaded?.name)
        // The backup was written before the update, so it still has no sketch.
        assertEquals(0, loaded?.segmentCount)

        val reopened = repository()
        reopened.reload()
        assertEquals(1, reopened.surveys.value.size)
        assertFalse(reopened.surveys.value.single().hasSketch)
    }

    @Test
    fun sortsSurveysByLastUpdate() = runBlocking {
        var now = 1_000L
        val ids = listOf(FIRST_ID, SECOND_ID)
        var next = 0
        val repository = JsonSurveyRepository(
            directory = directory,
            photoDirectory = photoDirectory,
            clock = { now },
            idGenerator = { ids[next++] },
        )
        repository.reload()

        val first = repository.create("First")
        now = 2_000L
        repository.create("Second")

        assertEquals(SECOND_ID, repository.surveys.value.first().id)

        now = 3_000L
        repository.update(first.copy(updatedAt = now))

        assertEquals(FIRST_ID, repository.surveys.value.first().id)
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
        const val SURVEY_ID = "00000000-0000-0000-0000-000000000001"
        const val FIRST_ID = "00000000-0000-0000-0000-000000000002"
        const val SECOND_ID = "00000000-0000-0000-0000-000000000003"
        const val SEGMENT_ID = "00000000-0000-0000-0000-000000000010"
    }
}
