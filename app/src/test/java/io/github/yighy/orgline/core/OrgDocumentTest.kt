package io.github.yighy.orgline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgDocumentTest {

    private val sample = """
        #+TITLE: Sample
        #+TODO: TODO NEXT | DONE

        * TODO First note   :work:urgent:
          SCHEDULED: <2026-10-05 Mon 09:00>
          :PROPERTIES:
          :CUSTOM:   value
          :END:
          Some content
          on two lines.

        ** Child
        Text
        * DONE Second
        CLOSED: [2026-10-01 Thu 10:00]
    """.trimIndent() + "\n"

    @Test
    fun splitsPrefaceAndSections() {
        val doc = OrgDocument.parse(sample)
        assertEquals("#+TITLE: Sample\n#+TODO: TODO NEXT | DONE\n\n", doc.preface)
        assertEquals(3, doc.sections.size)
        assertEquals(listOf(1, 2, 1), doc.sections.map { it.level })
        assertEquals(sample, doc.preface + doc.sections.joinToString("") { it.raw })
    }

    @Test
    fun readsNotes() {
        val notes = OrgNote.fromDocument(OrgDocument.parse(sample), "uri", "sample.org")
        val first = notes[0]
        assertEquals("TODO", first.state)
        assertEquals("First note", first.title)
        assertEquals(listOf("work", "urgent"), first.tags)
        assertEquals("<2026-10-05 Mon 09:00>", first.scheduled)
        assertEquals("value", first.property("CUSTOM"))
        assertTrue(first.content.contains("Some content"))
        assertFalse(first.isDone)
        assertTrue(notes[2].isDone)
    }

    @Test
    fun keywordsFromPreface() {
        assertEquals(Keywords(listOf("TODO", "NEXT"), listOf("DONE")), OrgDocument.parse(sample).keywords)
        assertEquals(Keywords.DEFAULT, OrgDocument.parse("* Note\n").keywords)
        assertEquals(
            Keywords(listOf("TODO", "WAIT"), listOf("DONE")),
            Keywords.fromPreface("#+SEQ_TODO: TODO(t) WAIT(w@/!) | DONE(d!)\n"),
        )
    }

    @Test
    fun rewriteKeepsOtherSectionsUntouched() {
        val doc = OrgDocument.parse(sample)
        val section = doc.sections[1]
        val edit = NoteEdit("Renamed child", "NEXT", listOf("home"), emptyList(), "New text")
        val newRaw = SectionEditor.rewrite(section.raw, section.level, doc.keywords, edit)
        val updated = doc.replaceSection(section, newRaw)

        assertEquals(doc.sections[0].raw, updated.sections[0].raw)
        assertEquals(doc.sections[2].raw, updated.sections[2].raw)
        // No tags before: Emacs' default column (tags end at column 77)
        val heading = "** NEXT Renamed child"
        assertEquals(heading + " ".repeat(77 - heading.length - 6) + ":home:\nNew text\n", updated.sections[1].raw)
    }

    @Test
    fun rewriteKeepsTagColumnAndIndentation() {
        val raw = "* TODO Buy groceries                                              :home:\n  SCHEDULED: <2026-10-05 Mon>\n  Milk.\n\n"
        val edit = NoteEdit("Buy food", "TODO", listOf("home"), emptyList(), "  Milk.")
        assertEquals(
            "* TODO Buy food                                                   :home:\n  SCHEDULED: <2026-10-05 Mon>\n  Milk.\n\n",
            SectionEditor.rewrite(raw, 1, Keywords.DEFAULT, edit),
        )
    }

    @Test
    fun rewriteKeepsPlanningAndTrailingBlankLine() {
        val doc = OrgDocument.parse(sample)
        val note = OrgNote.fromDocument(doc, "uri", "sample.org")[0]
        val section = doc.sections[0]
        val newRaw = SectionEditor.rewrite(section.raw, section.level, doc.keywords, note.toEdit().copy(title = "First"))

        val reparsed = OrgNote.fromDocument(doc.replaceSection(section, newRaw), "uri", "sample.org")[0]
        assertEquals("First", reparsed.title)
        assertEquals(note.scheduled, reparsed.scheduled)
        assertEquals(note.properties, reparsed.properties)
        assertEquals(note.content, reparsed.content)
        assertTrue(newRaw.endsWith("lines.\n\n"))
    }

    @Test
    fun addPropertyToExistingDrawer() {
        val raw = "* Note\n  SCHEDULED: <2026-10-05 Mon>\n  :PROPERTIES:\n  :A:        1\n  :END:\nBody\n"
        assertEquals(
            "* Note\n  SCHEDULED: <2026-10-05 Mon>\n  :PROPERTIES:\n  :A:        1\n  :ID:       abc\n  :END:\nBody\n",
            SectionEditor.addProperty(raw, "ID", "abc"),
        )
    }

    @Test
    fun addPropertyCreatesDrawerAfterPlanning() {
        assertEquals(
            "* Note\nDEADLINE: <2026-10-05 Mon>\n:PROPERTIES:\n:ID:       abc\n:END:\nBody\n",
            SectionEditor.addProperty("* Note\nDEADLINE: <2026-10-05 Mon>\nBody\n", "ID", "abc"),
        )
        assertEquals(
            "* Note\n:PROPERTIES:\n:ID:       abc\n:END:\n",
            SectionEditor.addProperty("* Note\n", "ID", "abc"),
        )
    }

    @Test
    fun keepsWindowsLineEndings() {
        val doc = OrgDocument.parse("* A\r\nText\r\n* B\r\n")
        assertEquals("* A\nText\n", doc.sections[0].raw)
        val updated = doc.replaceSection(doc.sections[1], SectionEditor.addProperty(doc.sections[1].raw, "ID", "x"))
        assertEquals("* A\r\nText\r\n* B\r\n:PROPERTIES:\r\n:ID:       x\r\n:END:\r\n", updated.toFileText())
    }

    @Test
    fun appendNote() {
        val doc = OrgDocument.parse("* A").appendNote("New", listOf("ID" to "u1"))
        assertEquals("* A\n* New\n:PROPERTIES:\n:ID:       u1\n:END:\n", doc.text)
        assertEquals("u1", OrgNote.fromDocument(doc, "uri", "f.org")[1].id)
    }

    @Test
    fun emptyFile() {
        val doc = OrgDocument.parse("")
        assertTrue(doc.sections.isEmpty())
        assertNull(doc.sections.firstOrNull())
    }
}
