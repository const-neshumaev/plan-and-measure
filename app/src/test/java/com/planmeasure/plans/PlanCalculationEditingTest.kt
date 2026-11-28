package com.planmeasure.plans

import com.planmeasure.measurements.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCalculationEditingTest {

    @Test
    fun splittingASelectedEdgeKeepsTheWholeWallInALengthCalculation() {
        val plan = rectanglePlan(
            calculation = PlanCalculation(
                id = "length",
                name = "Length",
                kind = PlanCalculationKind.LENGTH,
                edgeIds = listOf("bottom"),
                createdAt = 1L,
            ),
        )
        var nextId = 0

        val split = PlanEditing.splitEdge(
            document = plan,
            edgeId = "bottom",
            at = PlanPoint(2.0, 0.0),
            newId = { "new-${nextId++}" },
        )

        // The node gets "new-0", the second half of the wall gets "new-1".
        assertEquals(listOf("bottom", "new-1"), split.calculations.single().edgeIds)
        assertEquals(4_000.0, PlanMeasurements.measureLength(split, listOf("bottom", "new-1"))!!.lengthMillimeters, TOLERANCE)
    }

    @Test
    fun removingAnEdgeDropsItFromALengthCalculation() {
        val plan = rectanglePlan(
            calculation = PlanCalculation(
                id = "length",
                name = "Length",
                kind = PlanCalculationKind.LENGTH,
                edgeIds = listOf("bottom", "right"),
                createdAt = 1L,
            ),
        )

        val updated = PlanEditing.removeEdge(plan, "bottom") { "new" }

        assertEquals(listOf("right"), updated.calculations.single().edgeIds)
    }

    @Test
    fun anAreaCalculationBecomesInvalidWhenItsOutlineIsBroken() {
        val plan = rectanglePlan(
            calculation = PlanCalculation(
                id = "area",
                name = "Area",
                kind = PlanCalculationKind.AREA,
                edgeIds = listOf("bottom", "right", "top", "left"),
                createdAt = 1L,
            ),
        )

        val updated = PlanEditing.removeEdge(plan, "bottom") { "new" }

        assertFalse(PlanMeasurements.evaluate(updated, updated.calculations.single()).valid)
    }

    @Test
    fun clearingTheDrawingClearsCalculations() {
        val plan = rectanglePlan(
            calculation = PlanCalculation(
                id = "area",
                name = "Area",
                kind = PlanCalculationKind.AREA,
                edgeIds = listOf("bottom", "right", "top", "left"),
                createdAt = 1L,
            ),
        )

        val cleared = PlanEditing.clearDrawing(plan)

        assertTrue(cleared.calculations.isEmpty())
        assertTrue(cleared.contours.isEmpty())
    }

    private fun rectanglePlan(calculation: PlanCalculation): PlanDocument {
        val nodes = listOf(
            PlanNode("a", 0.0, 0.0),
            PlanNode("b", 4.0, 0.0),
            PlanNode("c", 4.0, 3.0),
            PlanNode("d", 0.0, 3.0),
        )
        val edges = listOf(
            PlanEdge("bottom", "a", "b"),
            // A scale needs a dimension to stay valid when an edit normalizes the document.
            PlanEdge(
                "right",
                "b",
                "c",
                dimension = PlanDimension(
                    valueMm = "3000",
                    source = MeasurementSource.MANUAL,
                    updatedAt = 1L,
                ),
            ),
            PlanEdge("top", "c", "d"),
            PlanEdge("left", "d", "a"),
        )
        return PlanDocument(
            id = "plan",
            name = "Plan",
            createdAt = 1L,
            mmPerUnit = 1000.0,
            nodes = nodes,
            contours = listOf(PlanContour("outline", edges)),
            calculations = listOf(calculation),
        )
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
