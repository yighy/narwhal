package io.github.yighy.orgline.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkupTest {

    private fun styles(text: String) = Markup.find(text).map { it.style to it.content }

    @Test
    fun findsEmphasis() {
        assertEquals(
            listOf(
                Markup.Style.Bold to "bold", Markup.Style.Italic to "two words", Markup.Style.Underline to "u",
                Markup.Style.Strike to "gone", Markup.Style.Verbatim to "a=b", Markup.Style.Code to "x()",
            ),
            styles("A *bold*, /two words/ _u_ (+gone+) =a=b= ~x()~."),
        )
    }

    @Test
    fun leavesOtherTextAlone() {
        // Bullets, paths, maths, dates, spaces inside the markers
        assertEquals(emptyList<Any>(), styles("* item\n- a/b/c and 2*3*4\n<2026-10-06 Tue +1w>\n* not bold *"))
        assertEquals(emptyList<Any>(), styles("https://example.com/path/to/x and snake_case_name"))
    }
}
