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
    fun renamesLinksStillShowingTheOldTitle() {
        val text = "See [[id:U1][Old (draft)]], [[id:u1][my notes]] and [[id:u2][Old (draft)]]."
        assertEquals(
            "See [[id:U1][New]], [[id:u1][my notes]] and [[id:u2][Old (draft)]].",
            IdLinks.renameLinks(text, "u1", "Old [draft]", "New"),
        )
        assertEquals(text, IdLinks.renameLinks(text, "u3", "Old [draft]", "New"))
    }

    @Test
    fun findsAllKindsOfLinks() {
        val text = "See https://www.orgzly.com. Mail mailto:me@x.org, call tel:+33123 or " +
            "[[https://example.com][the site]] (file:gtd.org) and [[id:u1][a note]] geo:48.8,2.3"
        assertEquals(
            listOf("https://www.orgzly.com", "mailto:me@x.org", "tel:+33123", "https://example.com", "file:gtd.org", "id:u1", "geo:48.8,2.3"),
            Links.find(text).map { it.target },
        )
        assertEquals("the site", Links.find(text)[3].shown)
        assertEquals("Go to the site", IdLinks.plainText("Go to [[https://example.com][the site]]"))
    }

    @Test
    fun suggestionsRankPrefixFirst() {
        fun note(title: String) = OrgNote("u", "f", 0, 1, null, false, null, title, emptyList(), null, null, null, emptyList(), "", "")
        val notes = listOf(note("Weekly shopping"), note("Shopping list"), note("Garage"))
        assertEquals(listOf("Shopping list", "Weekly shopping"), IdLinks.suggestions(notes, "shop").map { it.title })
        assertEquals(3, IdLinks.suggestions(notes, "").size)
    }
}
