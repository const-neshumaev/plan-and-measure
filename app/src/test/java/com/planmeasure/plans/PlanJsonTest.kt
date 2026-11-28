package com.planmeasure.plans

import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.units.LengthUnit
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
        assertEquals(LengthUnit.METERS, plan.unit)
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
            unit = LengthUnit.CENTIMETERS,
            createdAt = 1_000L,
            mmPerUnit = 500.0,
            scaleEdgeId = "edge-1",
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
                                valueMm = "3200",
                                source = MeasurementSource.MANUAL,
                                updatedAt = 2_000L,
                            ),
                        ),
                        PlanEdge(
                            id = "edge-2",
                            startNodeId = "node-b",
                            endNodeId = "node-a",
                            dimension = PlanDimension(
                                valueMm = "7260",
                                source = MeasurementSource.RANGFINDER,
                                updatedAt = 3_000L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
        assertEquals(PLAN_SCHEMA_VERSION, plan.schemaVersion)
        assertEquals(LengthUnit.CENTIMETERS, plan.unit)
        assertEquals(500.0, plan.mmPerUnit ?: 0.0, 0.0)
        assertEquals("edge-1", plan.scaleEdgeId)
    }

    @Test
    fun roundTripsCalculations() {
        val plan = PlanDocument(
            id = "00000000-0000-0000-0000-000000000006",
            name = "Hall",
            createdAt = 1_000L,
            calculations = listOf(
                PlanCalculation(
                    id = "calculation-1",
                    name = "Wall without window",
                    kind = PlanCalculationKind.AREA,
                    contourId = "contour-1",
                    subtractHoles = true,
                    createdAt = 2_000L,
                    comment = "Window in the living room",
                ),
                PlanCalculation(
                    id = "calculation-2",
                    name = "Skirting",
                    kind = PlanCalculationKind.LENGTH,
                    edgeIds = listOf("edge-1", "edge-2"),
                    createdAt = 3_000L,
                ),
            ),
        )

        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }

    @Test
    fun readsACalculationWithoutComment() {
        val text = """
            {
              "id": "00000000-0000-0000-0000-000000000008",
              "name": "Hall",
              "createdAt": 1,
              "calculations": [
                {
                  "id": "calculation-1",
                  "name": "Skirting",
                  "kind": "LENGTH",
                  "edgeIds": ["edge-1"],
                  "createdAt": 2
                }
              ]
            }
        """.trimIndent()

        val plan = PlanJson.decode(text)

        assertEquals("", plan.calculations.single().comment)
    }

    @Test
    fun readsADocumentWithoutCalculations() {
        val text = """
            {
              "schemaVersion": 5,
              "id": "00000000-0000-0000-0000-000000000007",
              "name": "Hall",
              "createdAt": 1
            }
        """.trimIndent()

        val plan = PlanJson.decode(text)

        assertTrue(plan.calculations.isEmpty())
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
        assertEquals(LengthUnit.METERS, plan.unit)
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
        assertEquals(LengthUnit.METERS, plan.unit)
        assertEquals(PlanPoint(21.0, 4.0), plan.nodes.single().let { PlanPoint(it.x, it.y) })
    }
}
