package io.github.yighy.orgline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdLinksTest {

    @Test
    fun detectsQueryBeforeCursor() {
        assertEquals(IdLinks.Query(4, 11, "shop"), IdLinks.queryAt("See id:shop", 11))
        assertEquals("", IdLinks.queryAt("id:", 3)?.text)
        assertEquals("big list", IdLinks.queryAt("x id:big list", 13)?.text)
        assertEquals("a", IdLinks.queryAt("(id:a", 5)?.text)
    }

    @Test
    fun ignoresExistingLinksAndWords() {
        assertNull(IdLinks.queryAt("[[id:abc", 8))
        assertNull(IdLinks.queryAt("valid:abc", 9))
        assertNull(IdLinks.queryAt("id:abc\nmore", 11))
        assertNull(IdLinks.queryAt("[[id:abc][Title]] done", 22))
    }

    @Test
    fun formatsAndFindsLinks() {
        val link = IdLinks.format("u1", "Shopping [list]")
        assertEquals("[[id:u1][Shopping (list)]]", link)

        val found = IdLinks.find("See $link and [[id:u2]].")
        assertEquals(listOf("u1", "u2"), found.map { it.id })
        assertEquals(listOf("Shopping (list)", null), found.map { it.description })
    }

    @Test
    fun suggestionsRankPrefixFirst() {
        fun note(title: String) = OrgNote("u", "f", 0, 1, null, false, null, title, emptyList(), null, null, null, emptyList(), "", "")
        val notes = listOf(note("Weekly shopping"), note("Shopping list"), note("Garage"))
        assertEquals(listOf("Shopping list", "Weekly shopping"), IdLinks.suggestions(notes, "shop").map { it.title })
        assertEquals(3, IdLinks.suggestions(notes, "").size)
    }
}
