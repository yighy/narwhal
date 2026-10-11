package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStatesTest {

    private fun note(raw: String) = OrgNote.fromDocument(OrgDocument.parse(raw), "u", "f.org").first()

    @Test
    fun toggleTargets() {
        val k = Keywords.DEFAULT
        assertEquals("DONE", NoteStates.toggleTarget(note("* TODO a\n"), k))
        assertEquals("DONE", NoteStates.toggleTarget(note("* NEXT a\n"), k))
        assertEquals("TODO", NoteStates.toggleTarget(note("* DONE a\n"), k))
        assertEquals("TODO", NoteStates.toggleTarget(note("* a\n"), k))
    }

    @Test
    fun doneAddsClosedAndTodoRemovesIt() {
        val done = NoteStates.apply(note("* TODO a\n").toEdit(), "DONE", Keywords.DEFAULT)
        assertFalse(done.shifted)
        assertEquals("DONE", done.edit.state)
        assertNotNull(done.edit.closed)
        assertTrue(done.edit.closed!!.startsWith("["))

        val back = NoteStates.apply(done.edit, "TODO", Keywords.DEFAULT)
        assertEquals("TODO", back.edit.state)
        assertNull(back.edit.closed)
    }

    @Test
    fun repeatingTaskMovesForward() {
        val result = NoteStates.apply(note("* TODO a\nSCHEDULED: <2026-10-05 Mon +1w>\n").toEdit(), "DONE", Keywords.DEFAULT)
        assertTrue(result.shifted)
        assertEquals("TODO", result.edit.state)
        assertNull(result.edit.closed)
        assertTrue(result.edit.scheduled!!.contains("+1w"))
        assertFalse(result.edit.scheduled!!.contains("2026-10-05"))
    }
}
