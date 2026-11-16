package com.planmeasure.ui.plans

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanDimensionCardTest {

    private val canvas = Size(400f, 800f)
    private val card = Size(300f, 200f)
    private val margin = 8f
    private val gap = 18f

    @Test
    fun centersTheCardOverTheWallAndPrefersTheSpaceAboveIt() {
        val offset = place(anchor = Offset(200f, 400f))

        assertOffset(Offset(50f, 182f), offset)
    }

    @Test
    fun flipsBelowWhenTheWallIsNearTheTop() {
        val offset = place(anchor = Offset(200f, 100f))

        assertOffset(Offset(50f, 118f), offset)
    }

    @Test
    fun liftsTheCardAboveTheKeyboard() {
        val offset = place(anchor = Offset(200f, 600f), visibleBottom = 400f)

        assertOffset(Offset(50f, 192f), offset)
    }

    @Test
    fun keepsTheCardInsideTheCanvasHorizontally() {
        assertOffset(Offset(8f, 182f), place(anchor = Offset(10f, 400f)))
        assertOffset(Offset(92f, 182f), place(anchor = Offset(390f, 400f)))
    }

    @Test
    fun keepsTheLeftMarginWhenTheCardIsWiderThanTheCanvas() {
        val offset = dimensionCardOffset(
            anchor = Offset(50f, 400f),
            card = Size(300f, 200f),
            canvas = Size(200f, 800f),
            visibleBottom = 800f,
            margin = margin,
            gap = gap,
        )

        assertEquals(margin, offset.x, 0.001f)
    }

    @Test
    fun fallsBackToTheMarginWhenTheVisibleAreaIsTooSmall() {
        val offset = place(anchor = Offset(200f, 30f), visibleBottom = 100f)

        assertOffset(Offset(50f, 8f), offset)
    }

    private fun place(anchor: Offset, visibleBottom: Float = canvas.height) = dimensionCardOffset(
        anchor = anchor,
        card = card,
        canvas = canvas,
        visibleBottom = visibleBottom,
        margin = margin,
        gap = gap,
    )

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
