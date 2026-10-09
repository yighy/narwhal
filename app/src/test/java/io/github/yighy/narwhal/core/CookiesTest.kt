package io.github.yighy.narwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiesTest {

    private fun note(state: String?, done: Boolean) = OrgNote("f", "f.org", 0, 2, state, done, null, "x", emptyList(), null, null, null, emptyList(), "", "")

    @Test
    fun itemCookiesAndParentBoxes() {
        val content = "- [ ] Valises [%]\n  - [X] Vêtements\n  - [ ] Trousse\n  - [ ] Chargeurs\n- [X] Hôtel [/]"
        assertEquals(
            "- [-] Valises [33%]\n  - [X] Vêtements\n  - [ ] Trousse\n  - [ ] Chargeurs\n- [X] Hôtel [/]",
            Cookies.updateContent(content),
        )
        val all = "- [ ] Valises [/]\n  - [X] a\n  - [x] b"
        assertEquals("- [X] Valises [2/2]\n  - [X] a\n  - [x] b", Cookies.updateContent(all))
    }

    @Test
    fun nestedLevelsCountOnlyDirectChildren() {
        val content = "- Tout [/]\n  - [ ] A\n    - [X] A1\n    - [X] A2\n  - [ ] B"
        assertEquals("- Tout [1/2]\n  - [X] A\n    - [X] A1\n    - [X] A2\n  - [ ] B", Cookies.updateContent(content))
    }

    @Test
    fun headingCountsTopLevelBoxesElseSubNotes() {
        val content = "- [X] Hôtel\n- [ ] Passeport\n  - [ ] photo"
        assertEquals("Voyage [1/2]", Cookies.updateTitle("Voyage [/]", Cookies.updateContent(content), emptyList()))
        val children = listOf(note("DONE", true), note("TODO", false), note(null, false), note("DONE", true))
        assertEquals("Projet [66%]", Cookies.updateTitle("Projet [%]", "", children))
        assertEquals("Rien [/]", Cookies.updateTitle("Rien [/]", "text", emptyList()))
    }

    @Test
    fun textWithoutBoxesIsUntouched() {
        val content = "See [1/2] here\n- item [/]"
        assertEquals(content, Cookies.updateContent(content))
        assertTrue(Cookies.COOKIE.containsMatchIn("x [3/4]"))
    }
}
