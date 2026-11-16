package com.planmeasure.ui.plans

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDimensionLabelsTest {

    private val label = Size(40f, 12f)

    @Test
    fun placesALabelAboveAHorizontalWall() {
        val placed = place(
            candidate("edge", Offset(100f, 200f), Offset(300f, 200f)),
            selectedEdgeId = null,
        )

        assertEquals(1, placed.size)
        assertOffset(Offset(180f, 182f), placed.single().topLeft)
    }

    @Test
    fun placesALabelBesideAVerticalWall() {
        val placed = place(
            candidate("edge", Offset(200f, 100f), Offset(200f, 300f)),
            selectedEdgeId = null,
        )

        assertOffset(Offset(168f, 194f), placed.single().topLeft)
    }

    @Test
    fun hidesALabelWhoseWallIsShorterThanTheText() {
        val placed = place(
            candidate("edge", Offset(100f, 200f), Offset(120f, 200f)),
            selectedEdgeId = null,
        )

        assertTrue(placed.isEmpty())
    }

    @Test
    fun keepsTheSelectedLabelEvenOnAShortWall() {
        val placed = place(
            candidate("edge", Offset(100f, 200f), Offset(120f, 200f)),
            selectedEdgeId = "edge",
        )

        assertEquals(1, placed.size)
    }

    @Test
    fun skipsALabelThatWouldOverlapAPlacedOne() {
        val placed = place(
            candidate("edge-a", Offset(100f, 200f), Offset(300f, 200f)),
            candidate("edge-b", Offset(120f, 200f), Offset(320f, 200f)),
            selectedEdgeId = null,
        )

        assertEquals(listOf("edge-a"), placed.map { it.edgeId })
    }

    @Test
    fun placesTheSelectedLabelBeforeItsNeighbours() {
        val placed = place(
            candidate("edge-a", Offset(100f, 200f), Offset(300f, 200f)),
            candidate("edge-b", Offset(120f, 200f), Offset(320f, 200f)),
            selectedEdgeId = "edge-b",
        )

        assertEquals(listOf("edge-b"), placed.map { it.edgeId })
    }

    @Test
    fun returnsNothingWithoutCandidates() {
        val placed = place(selectedEdgeId = null)

        assertTrue(placed.isEmpty())
    }

    private fun place(vararg candidates: DimensionLabelCandidate, selectedEdgeId: String?) =
        placeDimensionLabels(
            candidates = candidates.toList(),
            selectedEdgeId = selectedEdgeId,
            gap = 6f,
            spacing = 4f,
        )

    private fun candidate(edgeId: String, start: Offset, end: Offset) =
        DimensionLabelCandidate(edgeId = edgeId, start = start, end = end, size = label)

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
