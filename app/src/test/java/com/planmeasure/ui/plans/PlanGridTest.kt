package com.planmeasure.ui.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanGridTest {

    private val minimumSpacing = 8f

    @Test
    fun showsTheFinestStepThatStaysReadable() {
        // A 200-unit sheet fitted into a phone is about 1.5 dp per unit, and the same sheet
        // zoomed to the maximum about 24 dp.
        assertEquals(10, gridStepFor(scale = 1.48f, minimumSpacing = minimumSpacing))
        assertEquals(5, gridStepFor(scale = 2.96f, minimumSpacing = minimumSpacing))
        assertEquals(2, gridStepFor(scale = 5.9f, minimumSpacing = minimumSpacing))
        assertEquals(1, gridStepFor(scale = 23.7f, minimumSpacing = minimumSpacing))
    }

    @Test
    fun coarsensTheGridOnAZoomedOutSheet() {
        assertEquals(100, gridStepFor(scale = 0.1f, minimumSpacing = minimumSpacing))
        // A sheet far beyond the coordinate limit has no readable step at all.
        assertNull(gridStepFor(scale = 0.0001f, minimumSpacing = minimumSpacing))
        assertNull(gridStepFor(scale = 0f, minimumSpacing = minimumSpacing))
        assertNull(gridStepFor(scale = Float.NaN, minimumSpacing = minimumSpacing))
    }

    @Test
    fun emphasizesEveryFifthLineOfTheShownStep() {
        assertEquals(0.5f, gridLineAlpha(index = 0, step = 10), 0.001f)
        assertEquals(0.5f, gridLineAlpha(index = 50, step = 10), 0.001f)
        assertEquals(0.22f, gridLineAlpha(index = 20, step = 10), 0.001f)
        assertEquals(0.22f, gridLineAlpha(index = 5, step = 5), 0.001f)
        assertEquals(0.5f, gridLineAlpha(index = 25, step = 5), 0.001f)
    }
}
