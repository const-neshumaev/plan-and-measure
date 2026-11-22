package com.planmeasure.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonTest {

    @Test
    fun roundTripsDocument() {
        val project = ProjectDocument(
            id = "00000000-0000-0000-0000-000000000001",
            name = "Flat",
            createdAt = 1_000L,
            updatedAt = 2_000L,
            folders = listOf(
                ProjectFolder("folder-1", "Kitchen"),
                ProjectFolder("folder-2", "Shelf", "folder-1"),
            ),
            objects = listOf(
                ProjectObjectRef(
                    kind = ProjectObjectKind.MEASUREMENT_LIST,
                    refId = "list-1",
                    folderId = "folder-1",
                ),
                ProjectObjectRef(
                    kind = ProjectObjectKind.PLAN,
                    refId = "plan-1",
                ),
            ),
        )

        assertEquals(project, ProjectJson.decode(ProjectJson.encode(project)))
    }

    @Test
    fun ignoresUnknownKeysAndFillsDefaults() {
        val text = """
            {
              "id": "00000000-0000-0000-0000-000000000002",
              "name": "Hall",
              "createdAt": 1,
              "futureField": true
            }
        """.trimIndent()

        val project = ProjectJson.decode(text)

        assertEquals(PROJECT_SCHEMA_VERSION, project.schemaVersion)
        assertEquals(1L, project.updatedAt)
        assertTrue(project.folders.isEmpty())
        assertTrue(project.objects.isEmpty())
    }

    @Test
    fun readsReferencesByKindName() {
        val text = """
            {
              "id": "00000000-0000-0000-0000-000000000003",
              "name": "Hall",
              "createdAt": 1,
              "objects": [
                {"kind": "MEASUREMENT_LIST", "refId": "list-1"},
                {"kind": "PLAN", "refId": "plan-1", "folderId": "folder-1"}
              ]
            }
        """.trimIndent()

        val project = ProjectJson.decode(text)

        assertEquals(
            listOf(ProjectObjectKind.MEASUREMENT_LIST, ProjectObjectKind.PLAN),
            project.objects.map { it.kind },
        )
        assertEquals("folder-1", project.objects.last().folderId)
    }
}
