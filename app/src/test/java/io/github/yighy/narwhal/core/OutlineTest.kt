package io.github.yighy.narwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutlineTest {

    private val doc = OrgDocument.parse("#+TITLE: T\n\n* A\na\n** A1\n*** A1a\n* B\nb\n* C")

    @Test
    fun rootsDropNestedSelections() {
        assertEquals(listOf(0, 3), Outline.roots(doc, listOf(3, 2, 0)))
    }

    @Test
    fun extractAndDelete() {
        val subtrees = Outline.extract(doc, listOf(1, 4))
        assertEquals(listOf(Subtree("** A1\n*** A1a\n", 2), Subtree("* C\n", 1)), subtrees)
        assertEquals("#+TITLE: T\n\n* A\na\n* B\nb\n", Outline.delete(doc, listOf(1, 4)).text)
    }

    @Test
    fun insertWithNewLevel() {
        val a1 = Outline.extract(doc, listOf(1))
        // As a top-level note before B
        assertEquals("#+TITLE: T\n\n* A\na\n** A1\n*** A1a\n* A1\n** A1a\n* B\nb\n* C", Outline.insert(doc, 3, 1, a1).text)
        // Under C (at the end of a file without final newline)
        val (at, level) = Outline.target(doc, 4, Placement.Under)
        assertEquals("#+TITLE: T\n\n* A\na\n** A1\n*** A1a\n* B\nb\n* C\n** A1\n*** A1a\n", Outline.insert(doc, at, level, a1).text)
    }

    @Test
    fun placements() {
        assertEquals(0 to 1, Outline.target(doc, 0, Placement.Above))
        assertEquals(3 to 2, Outline.target(doc, 0, Placement.Under))
        assertEquals(3 to 1, Outline.target(doc, 0, Placement.Below))
    }

    @Test
    fun moveUpAndDown() {
        val (up, upIndex) = Outline.moveUp(doc, 3)!!
        assertEquals("#+TITLE: T\n\n* B\nb\n* A\na\n** A1\n*** A1a\n* C", up.text)
        assertEquals(0, upIndex)
        val (down, downIndex) = Outline.moveDown(doc, 0)!!
        assertEquals("#+TITLE: T\n\n* B\nb\n* A\na\n** A1\n*** A1a\n* C", down.text)
        assertEquals(1, downIndex)
        assertEquals("#+TITLE: T\n\n* A\na\n** A1\n*** A1a\n* C\n* B\nb\n", Outline.moveDown(doc, 3)!!.first.text)
        assertNull(Outline.moveUp(doc, 0))
        assertNull(Outline.moveUp(doc, 1)) // first child
        assertNull(Outline.moveDown(doc, 4)) // last note
    }

    @Test
    fun promoteAndDemote() {
        assertEquals("#+TITLE: T\n\n* A\na\n* A1\n** A1a\n* B\nb\n* C", Outline.shift(doc, listOf(1), -1).text)
        assertEquals("#+TITLE: T\n\n* A\na\n** A1\n*** A1a\n** B\nb\n* C", Outline.shift(doc, listOf(3), 1).text)
        // Level 1 can't be promoted
        assertEquals(doc.text, Outline.shift(doc, listOf(0), -1).text)
    }

    @Test
    fun replaceSeveralSections() {
        val updated = Outline.replaceSections(doc, mapOf(0 to "* X\n", 3 to "* Y\n"))
        assertEquals("#+TITLE: T\n\n* X\n** A1\n*** A1a\n* Y\n* C", updated.text)
    }
}
