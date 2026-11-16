package com.planmeasure.plans

import com.planmeasure.measurements.LengthUnit
import com.planmeasure.measurements.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun roundTripsDimensions() {
        val plan = PlanDocument(
            id = "00000000-0000-0000-0000-000000000004",
            name = "Kitchen",
            createdAt = 1_000L,
            nodes = listOf(
                PlanNode("node-a", 2.0, 2.0),
                PlanNode("node-b", 6.0, 2.0),
            ),
            contours = listOf(
                PlanContour(
                    id = "contour-1",
                    edges = listOf(
                        PlanEdge(
                            id = "edge-1",
                            startNodeId = "node-a",
                            endNodeId = "node-b",
                            dimension = PlanDimension(
                                valueText = "3.20",
                                unit = LengthUnit.METERS,
                                source = MeasurementSource.MANUAL,
                                updatedAt = 2_000L,
                            ),
                        ),
                        PlanEdge(
                            id = "edge-2",
                            startNodeId = "node-b",
                            endNodeId = "node-a",
                            dimension = PlanDimension(
                                valueText = "7.26",
                                unit = LengthUnit.FEET,
                                source = MeasurementSource.RANGFINDER,
                                updatedAt = 3_000L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
        assertEquals(3, plan.schemaVersion)
    }

    @Test
    fun readsADocumentWrittenBeforeDimensions() {
        val text = """
            {
              "schemaVersion": 2,
              "id": "00000000-0000-0000-0000-000000000005",
              "name": "Hall",
              "createdAt": 1,
              "nodes": [
                {"id": "a", "x": 2.0, "y": 2.0},
                {"id": "b", "x": 8.0, "y": 2.0}
              ],
              "contours": [
                {"id": "c", "edges": [{"id": "e", "startNodeId": "a", "endNodeId": "b"}]}
              ]
            }
        """.trimIndent()

        val plan = PlanJson.decode(text)

        assertNull(plan.edges.single().dimension)
        assertEquals(2, plan.schemaVersion)
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
