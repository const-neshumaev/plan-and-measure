package com.planmeasure.ui.plans

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingMagnifierTest {

    private val canvas = Size(width = 1000f, height = 2000f)
    private val radius = 100f
    private val gap = 20f

    @Test
    fun loupeSitsAboveTheFinger() {
        val center = magnifierCenter(Offset(500f, 900f), canvas, radius, gap)

        assertEquals(500f, center.x, 0.01f)
        assertEquals(780f, center.y, 0.01f)
    }

    @Test
    fun loupeFlipsBelowNearTheTopEdge() {
        val center = magnifierCenter(Offset(500f, 150f), canvas, radius, gap)

        assertEquals(270f, center.y, 0.01f)
    }

    @Test
    fun loupeStaysInsideHorizontalEdges() {
        assertEquals(radius, magnifierCenter(Offset(10f, 900f), canvas, radius, gap).x, 0.01f)
        assertEquals(900f, magnifierCenter(Offset(990f, 900f), canvas, radius, gap).x, 0.01f)
    }

    @Test
    fun loupeStaysInsideCanvasSmallerThanItself() {
        val center = magnifierCenter(Offset(10f, 10f), Size(50f, 50f), radius, gap)

        assertEquals(radius, center.x, 0.01f)
        assertEquals(radius, center.y, 0.01f)
    }
}
