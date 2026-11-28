package com.planmeasure.ui.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTreeRailsTest {

    @Test
    fun outerRailContinuesWhileAncestorHasLaterChildren() {
        // A
        //   a1
        //     a1x
        //     a1y
        //   a2
        // B
        val depths = listOf(0, 1, 2, 2, 1, 0)

        assertEquals(
            listOf(
                emptyList<Boolean>(),
                listOf(true),
                listOf(true, true),
                listOf(true, false),
                listOf(false),
                emptyList(),
            ),
            railContinuations(depths),
        )
    }

    @Test
    fun lastChildTurnsEveryRailIntoACorner() {
        // A
        //   a1
        //     a1x
        val depths = listOf(0, 1, 2)

        assertEquals(
            listOf(
                emptyList<Boolean>(),
                listOf(false),
                listOf(false, false),
            ),
            railContinuations(depths),
        )
    }

    @Test
    fun childRailContinuesAlongItsSiblingsWithinTheSubtree() {
        // A
        //   a1
        //     a1x
        //     a1y
        val depths = listOf(0, 1, 2, 2)

        assertEquals(
            listOf(
                emptyList<Boolean>(),
                listOf(false),
                listOf(false, true),
                listOf(false, false),
            ),
            railContinuations(depths),
        )
    }
}
