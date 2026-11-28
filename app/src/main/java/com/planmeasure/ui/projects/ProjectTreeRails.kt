package com.planmeasure.ui.projects

/**
 * For every visible tree row, tells for each ancestor rail whether it continues below the row.
 *
 * Row `i` has `depths[i]` rails, outermost first. A rail continues while the ancestor at that
 * level still has a child row further down; the deepest rail that does not continue stops at the
 * branch point, which turns the branch into an "L" instead of a "T" with a dangling tail.
 */
internal fun railContinuations(depths: List<Int>): List<List<Boolean>> =
    depths.mapIndexed { index, depth ->
        List(depth) { level ->
            var next = index + 1
            var continues = false
            while (next < depths.size && depths[next] > level) {
                if (depths[next] == level + 1) {
                    continues = true
                    break
                }
                next++
            }
            continues
        }
    }
