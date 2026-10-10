package io.github.yighy.narwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeLayoutTest {

    // * A / ** A1 / *** A1a / ** A2 / * B
    private val levels = listOf(1, 2, 3, 2, 1)

    @Test
    fun placesLeavesOnRowsAndParentsBetweenTheirChildren() {
        val nodes = TreeLayout.layout(levels)
        assertEquals(listOf(-1, 0, 1, 2, 3, 4), nodes.map { it.index })
        assertEquals(listOf(0, 1, 2, 3, 2, 1), nodes.map { it.depth })
        // A1a 0, A2 1, B 2; A1 across A1a, A between A1 and A2, the root between A and B
        assertEquals(listOf(1.25f, 0.5f, 0f, 0f, 1f, 2f), nodes.map { it.row })
        assertEquals(listOf(-1, 0, 1, 2, 1, 0), nodes.map { it.parent })
        assertEquals(listOf(true, true, true, false, false, false), nodes.map { it.hasChildren })
    }

    @Test
    fun foldedNotesHideTheirChildren() {
        val nodes = TreeLayout.layout(levels, folded = setOf(0))
        assertEquals(listOf(-1, 0, 4), nodes.map { it.index })
        assertEquals(listOf(0.5f, 0f, 1f), nodes.map { it.row })
        assertEquals(listOf(0, 3, 0), nodes.map { it.hidden })
    }

    @Test
    fun levelJumpsAndEmptyNotebooks() {
        // * A / *** B / ** C: B and C are both A's children
        val nodes = TreeLayout.layout(listOf(1, 3, 2))
        assertEquals(listOf(0, 1, 2, 2), nodes.map { it.depth })
        assertEquals(listOf(-1, 0, 1, 1), nodes.map { it.parent })

        assertEquals(listOf(TreeLayout.Node(-1, 0, 0f, -1, false, 0)), TreeLayout.layout(emptyList()))
    }

    @Test
    fun keysFollowNotesChangedInPlaceAndMoved() {
        val keys = TreeKeys()
        assertEquals(listOf("A", "B", "C"), keys.update(listOf("A", "B", "C")))
        // B changed in place (its state): keeps its key
        assertEquals(listOf("A", "B", "C"), keys.update(listOf("A", "TODO B", "C")))
        // Moved: the keys follow the texts
        assertEquals(listOf("C", "A", "B"), keys.update(listOf("C", "A", "TODO B")))
        // A new note gets its text as key, and an added one does not take a gone one's key
        assertEquals(listOf("C", "A", "B", "D"), keys.update(listOf("C", "A", "TODO B", "D")))
        assertEquals(listOf("C", "E", "B", "D", "F"), keys.update(listOf("C", "E", "TODO B", "D", "F")))
    }
}
