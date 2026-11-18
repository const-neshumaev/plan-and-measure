package com.planmeasure.ui.plans

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.planmeasure.plans.PlanPoint
import com.planmeasure.plans.PlanSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanViewportTest {

    private val canvas = Size(400f, 800f)

    // Explicit, so the arithmetic does not depend on the default sheet of a new plan.
    private val world = PlanSize(width = 20.0, height = 28.0)
    private val padding = 10f

    @Test
    fun fitsTheWholeSheetInsideTheCanvas() {
        val transform = planTransform(canvas, world, PlanViewport(), padding)

        assertEquals(19f, transform.scale, 0.001f)
        assertOffset(Offset(10f, 134f), transform.toPx(PlanPoint(0.0, 0.0)))
        assertOffset(Offset(390f, 666f), transform.toPx(PlanPoint(20.0, 28.0)))
    }

    @Test
    fun mapsPixelsBackToTheSamePoint() {
        val transform = planTransform(canvas, world, PlanViewport(), padding)

        assertPoint(PlanPoint(7.0, 11.0), transform.toWorld(transform.toPx(PlanPoint(7.0, 11.0))))
    }

    @Test
    fun zoomsAroundTheFingers() {
        val focus = Offset(200f, 400f)
        val before = planTransform(canvas, world, PlanViewport(), padding)

        val zoomed = PlanViewport().transformedBy(
            canvas = canvas,
            world = world,
            padding = padding,
            zoomChange = 2f,
            panChange = Offset.Zero,
            focus = focus,
        )
        val after = planTransform(canvas, world, zoomed, padding)

        assertEquals(2f, zoomed.zoom, 0.001f)
        assertPoint(before.toWorld(focus), after.toWorld(focus))
    }

    @Test
    fun zoomsOutToLeaveRoomAroundTheSheet() {
        val zoomedOut = PlanViewport().transformedBy(
            canvas = canvas,
            world = world,
            padding = padding,
            zoomChange = 0.5f,
            panChange = Offset.Zero,
            focus = Offset(200f, 400f),
        )
        val transform = planTransform(canvas, world, zoomedOut, padding)

        assertEquals(0.5f, zoomedOut.zoom, 0.001f)
        assertTrue(transform.size(world).width < canvas.width)
        assertTrue(transform.size(world).height < canvas.height)
    }

    @Test
    fun stopsZoomingOutAtTheMinimum() {
        var viewport = PlanViewport()
        repeat(4) {
            viewport = viewport.transformedBy(
                canvas = canvas,
                world = world,
                padding = padding,
                zoomChange = 0.5f,
                panChange = Offset.Zero,
                focus = Offset(200f, 400f),
            )
        }

        assertEquals(0.5f, viewport.zoom, 0.001f)
        assertEquals(Offset.Zero, viewport.pan)
    }

    @Test
    fun stopsZoomingInAtTheMaximum() {
        var viewport = PlanViewport()
        repeat(5) {
            viewport = viewport.transformedBy(
                canvas = canvas,
                world = world,
                padding = padding,
                zoomChange = 10f,
                panChange = Offset.Zero,
                focus = Offset(200f, 400f),
            )
        }

        assertEquals(16f, viewport.zoom, 0.001f)
    }

    @Test
    fun ignoresPanWhileTheSheetFits() {
        val transform = planTransform(canvas, world, PlanViewport(pan = Offset(100f, 100f)), padding)

        assertOffset(Offset(10f, 134f), transform.toPx(PlanPoint(0.0, 0.0)))
    }

    @Test
    fun pansAZoomedSheetWithinReach() {
        val transform = planTransform(
            canvas = canvas,
            world = world,
            viewport = PlanViewport(zoom = 2f, pan = Offset(50f, 50f)),
            padding = padding,
        )

        assertOffset(Offset(-130f, -82f), transform.toPx(PlanPoint(0.0, 0.0)))
    }

    @Test
    fun stopsPanningAtTheSheetBorder() {
        val transform = planTransform(
            canvas = canvas,
            world = world,
            viewport = PlanViewport(zoom = 2f, pan = Offset(1000f, 1000f)),
            padding = padding,
        )

        assertOffset(Offset(0f, 0f), transform.toPx(PlanPoint(0.0, 0.0)))
        assertOffset(Offset(760f, 1064f), transform.toPx(PlanPoint(20.0, 28.0)))
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    private fun assertPoint(expected: PlanPoint, actual: PlanPoint) {
        assertEquals(expected.x, actual.x, 0.001)
        assertEquals(expected.y, actual.y, 0.001)
    }
}
