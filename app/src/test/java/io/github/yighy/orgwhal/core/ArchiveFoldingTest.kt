package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ArchiveFoldingTest {

    @Test
    fun copiesLoseTheirIds() {
        val text = "* A\n:PROPERTIES:\n:ID: 1\n:EFFORT: 1:00\n:END:\nText\n** B\n  :PROPERTIES:\n  :ID: 2\n  :END:\n** C\n"
        assertEquals("* A\n:PROPERTIES:\n:EFFORT: 1:00\n:END:\nText\n** B\n** C\n", Outline.withoutIds(text))
        // A :ID: line in the text, out of a drawer, stays
        assertEquals("* A\n:ID: not a property\n", Outline.withoutIds("* A\n:ID: not a property\n"))
    }

    private fun notes(text: String) = OrgDocument.parse(text).let { OrgNote.fromDocument(it, "u", "f.org") to it.preface }

    @Test
    fun startupAndVisibility() {
        val body = "* A\n** A1\n*** A1a\n** A2\n* B\n** B1\n* C\n"
        fun folded(preface: String, text: String = body): List<String> {
            val (n, p) = notes(preface + text)
            return Folding.initial(p, n).map { n[it].title }
        }
        assertEquals(emptyList<String>(), folded(""))
        // As in Org, unfolding A then shows A1 folded
        assertEquals(listOf("A", "A1", "B"), folded("#+STARTUP: overview\n"))
        assertEquals(listOf("A1"), folded("#+STARTUP: show2levels\n"))
        // The last word about folding wins
        assertEquals(emptyList<String>(), folded("#+STARTUP: overview indent\n#+STARTUP: showall\n"))
        // VISIBILITY: children shows A's children folded; folded folds B
        val visible = "* A\n:PROPERTIES:\n:VISIBILITY: children\n:END:\n** A1\n*** A1a\n** A2\n* B\n:PROPERTIES:\n:VISIBILITY: folded\n:END:\n** B1\n"
        assertEquals(listOf("B", "A1"), folded("", visible).sortedBy { it != "B" })
        assertEquals(listOf("A1", "B"), folded("#+STARTUP: overview\n", visible))
    }

    @Test
    fun archivedAsEmacsDoes() {
        val doc = OrgDocument.parse("#+TITLE: Work\n* Projects\n** DONE Ship it\nDone.\n*** Detail\n* Other\n")
        val entry = Archive.entry(doc, 1, "sub/work.org", LocalDateTime.of(2026, 10, 10, 14, 3))
        assertEquals(
            "** DONE Ship it\n:PROPERTIES:\n:ARCHIVE_TIME: 2026-10-10 Sat 14:03\n:ARCHIVE_FILE: sub/work.org\n" +
                ":ARCHIVE_OLPATH: Projects\n:ARCHIVE_CATEGORY: work\n:ARCHIVE_TODO: DONE\n:END:\nDone.\n*** Detail\n",
            entry.text.replace(Regex(":(\\w+):[ \\t]+"), ":$1: "),
        )
        assertEquals("sub/work.org_archive", Archive.path("sub/work.org"))
        // A new archive file gets Emacs' header; the entry goes at the end, as a top-level note
        val archive = Archive.append(null, "work.org", listOf(entry))
        assert(archive.startsWith(Archive.header("work.org")))
        assert(archive.contains("\n* DONE Ship it\n")) { archive }
        assert(archive.endsWith("Done.\n** Detail\n")) { archive }
        val again = Archive.append(archive, "work.org", listOf(Subtree("* Other\n", 1)))
        assert(again.startsWith(archive) && again.endsWith("* Other\n")) { again }
    }
}
