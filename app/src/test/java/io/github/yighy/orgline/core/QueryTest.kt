package io.github.yighy.orgline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class QueryTest {

    private val doc = OrgDocument.parse(
        """
        * TODO Write report                                        :work:
          SCHEDULED: <2026-10-05 Mon 09:00>
        ** NEXT [#A] Collect numbers
           DEADLINE: <2026-10-07 Wed>
        * DONE Pay rent                                            :home:
          CLOSED: [2026-10-04 Sun 10:00]
        * Shopping list                                            :home:
          milk and "fresh" bread
        * WAITING Call back
          SCHEDULED: <2026-10-01 Thu>
        """.trimIndent() + "\n",
    )
    private val notes = OrgNote.fromDocument(doc, "uri", "work.org")
    private val inherited = NoteTree.inheritedTags(notes)

    private val ctx = SearchQuery.Context(
        now = LocalDateTime.of(2026, 10, 5, 12, 0), // Monday noon
        keywords = { Keywords.DEFAULT },
        notebookName = { "work" },
        inheritedTags = { inherited[it.index] },
    )

    private fun titles(query: String) = SearchQuery.parse(query).apply(notes, ctx).map { it.title }

    @Test
    fun states() {
        assertEquals(listOf("Write report"), titles("i.todo"))
        assertEquals(listOf("Write report", "Collect numbers", "Call back"), titles("it.todo"))
        assertEquals(listOf("Pay rent"), titles("it.done"))
        assertEquals(listOf("Shopping list"), titles("it.none"))
        assertEquals(listOf("Collect numbers", "Pay rent", "Shopping list", "Call back"), titles(".i.todo"))
    }

    @Test
    fun tagsNotebookPriority() {
        // Inherited from "Write report"
        assertEquals(listOf("Write report", "Collect numbers"), titles("t.work"))
        assertEquals(listOf("Write report"), titles("tn.work"))
        assertEquals(notes.size, titles("b.work").size)
        assertTrue(titles("b.other").isEmpty())
        assertEquals(listOf("Collect numbers"), titles("p.a"))
        // Unset priority counts as B, but not for ps.
        assertEquals(4, titles("p.b").size)
        assertTrue(titles("ps.b").isEmpty())
    }

    @Test
    fun times() {
        // Scheduled by today: today 09:00 and the overdue one
        assertEquals(listOf("Write report", "Call back"), titles("s.today"))
        assertEquals(listOf("Write report"), titles("s.eq.today"))
        assertEquals(listOf("Call back"), titles("s.lt.today"))
        assertEquals(listOf("Collect numbers"), titles("d.le.3d"))
        assertTrue(titles("d.le.1d").isEmpty())
        assertEquals(listOf("Pay rent"), titles("c.yesterday"))
        assertEquals(listOf("Collect numbers", "Pay rent", "Shopping list"), titles("s.none"))
        assertEquals(listOf("Write report", "Call back"), titles("s.le.now"))
    }

    @Test
    fun textAndLogic() {
        assertEquals(listOf("Shopping list"), titles("milk"))
        assertEquals(listOf("Shopping list"), titles("\"and \\\"fresh\\\"\""))
        assertEquals(listOf("Write report", "Pay rent"), titles("i.todo or i.done"))
        // AND binds tighter than OR
        assertEquals(listOf("Write report", "Shopping list"), titles("i.todo t.work or t.home it.none"))
        assertEquals(listOf("Write report", "Collect numbers"), titles("t.work (i.todo or i.next)"))
        // Unknown prefix is plain text
        assertTrue(titles("x.y").isEmpty())
    }

    @Test
    fun sortOrders() {
        assertEquals(listOf("Call back", "Write report"), titles("s.today o.s"))
        assertEquals(listOf("Write report", "Call back"), titles("s.today .o.s"))
        assertEquals(listOf("Call back", "Collect numbers", "Pay rent", "Shopping list", "Write report"), titles("o.title"))
        // Priority A first; unset ones count as B
        assertEquals("Collect numbers", titles("o.p").first())
    }
}
