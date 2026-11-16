package com.planmeasure.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanJsonTest {

    @Test
    fun roundTripsDocument() {
        val plan = PlanDocument(
            id = "00000000-0000-0000-0000-000000000001",
            name = "Living room",
            createdAt = 1_000L,
            updatedAt = 2_000L,
            world = PlanSize(width = 24.0, height = 32.0),
            nodes = listOf(
                PlanNode("node-a", 2.0, 2.0),
                PlanNode("node-b", 8.0, 2.0),
                PlanNode("node-c", 8.0, 7.5),
            ),
            contours = listOf(
                PlanContour(
                    id = "contour-1",
                    edges = listOf(
                        PlanEdge("edge-1", "node-a", "node-b"),
                        PlanEdge("edge-2", "node-b", "node-c"),
                    ),
                ),
            ),
        )

        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
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

        val plan = PlanJson.decode(text)

        assertEquals(PLAN_SCHEMA_VERSION, plan.schemaVersion)
        assertEquals(PlanSize(), plan.world)
        assertTrue(plan.nodes.isEmpty())
        assertTrue(plan.contours.isEmpty())
        assertEquals(1L, plan.updatedAt)
    }

    @Test
    fun keepsTheSheetOfADocumentWrittenBeforeSheetsWereStored() {
        val text = """
            {
              "schemaVersion": 1,
              "id": "00000000-0000-0000-0000-000000000003",
              "name": "Hall",
              "createdAt": 1,
              "nodes": [{"id": "a", "x": 21.0, "y": 4.0}]
            }
        """.trimIndent()

        val plan = PlanJson.decode(text)

        // A version 1 document had a fixed sheet, so its coordinates must be read as is.
        assertEquals(PlanSize(), plan.world)
        assertEquals(PlanPoint(21.0, 4.0), plan.nodes.single().let { PlanPoint(it.x, it.y) })
    }
}
