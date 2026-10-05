package io.github.yighy.orgline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NoteTreeTest {

    private val doc = OrgDocument.parse(
        "#+TITLE: My notebook\n#+STARTUP: overview\nIntro text.\n\n* A\n** A1\n*** A1a\n** A2\n* B\n",
    )
    private val notes = OrgNote.fromDocument(doc, "uri", "file.org")

    @Test
    fun treeHelpers() {
        assertTrue(NoteTree.hasChildren(notes, 0))
        assertTrue(NoteTree.hasChildren(notes, 1))
        assertFalse(NoteTree.hasChildren(notes, 3))
        assertFalse(NoteTree.hasChildren(notes, 4))
        assertEquals(listOf("A", "A1"), NoteTree.ancestors(notes, 2).map { it.title })
        assertEquals(listOf("A"), NoteTree.ancestors(notes, 3).map { it.title })
        assertEquals(listOf("A", "B"), NoteTree.visible(notes) { it.title == "A" }.map { it.title })
        assertEquals(listOf("A", "A1", "A2", "B"), NoteTree.visible(notes) { it.title == "A1" }.map { it.title })
        assertEquals(listOf(emptyList(), listOf(1), listOf(1, 2), listOf(1), emptyList()), NoteTree.ancestorLevels(notes))

        // Level jump: "*" then "***"
        val jump = OrgNote.fromDocument(OrgDocument.parse("* A\n*** A1\n** A2\n"), "u", "f")
        assertEquals(listOf(emptyList(), listOf(1), listOf(1)), NoteTree.ancestorLevels(jump))
    }

    @Test
    fun notebookInfo() {
        assertEquals("My notebook", Notebooks.title(doc, "file.org"))
        assertEquals("other", Notebooks.title(OrgDocument.parse("* x\n"), "other.org"))
        assertEquals("Intro text.", Notebooks.prefaceText(doc))
    }

    @Test
    fun timestamps() {
        assertEquals("<2026-10-05 Mon 09:30>", OrgTime.build(LocalDate.of(2026, 10, 5), LocalTime.of(9, 30)))
        assertEquals("<2026-10-06 Tue +1w>", OrgTime.build(LocalDate.of(2026, 10, 6), null, "<2026-10-05 Mon 08:00 +1w>"))

        val parts = OrgTime.parts("<2026-10-05 Mon 09:30 +1d>")!!
        assertEquals(LocalDate.of(2026, 10, 5), parts.date)
        assertEquals(LocalTime.of(9, 30), parts.time)
        assertEquals("+1d", parts.repeater)
        assertNull(OrgTime.parts("not a date"))
    }

    @Test
    fun repeaters() {
        val day = LocalDate.of(2026, 10, 5)
        assertEquals("<2026-10-05 Mon .+2w>", OrgTime.build(day, null, "<2026-10-05 Mon +1d>", ".+2w"))
        assertEquals("<2026-10-05 Mon>", OrgTime.build(day, null, "<2026-10-05 Mon +1d>", null))
        assertEquals("<2026-10-05 Mon ++1m>", OrgTime.build(day, null, repeater = "++1m"))

        assertEquals(OrgTime.Repeat(".+", 2, 'd', "/4d"), OrgTime.Repeat.parse(".+2d/4d"))
        assertEquals("++3w", OrgTime.Repeat("++", 3, 'w').toString())
        assertNull(OrgTime.Repeat.parse("every week"))
    }
}
