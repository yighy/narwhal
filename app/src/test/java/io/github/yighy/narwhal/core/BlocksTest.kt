package io.github.yighy.narwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocksTest {

    @Test
    fun splitsTablesBlocksAndDrawers() {
        val text = ":LOGBOOK:\nCLOCK: [2026-10-09 Fri 10:00]\n:END:\nIntro\n| a | b |\n|---+---|\n| 1 | 2 |\n#+BEGIN_SRC kotlin\nval x = 1\n#+END_SRC\nEnd"
        val blocks = Blocks.parse(text)
        assertEquals(5, blocks.size)
        assertEquals("LOGBOOK", (blocks[0] as Blocks.Drawer).name)
        val intro = blocks[1] as Blocks.Text
        assertEquals("Intro", intro.text)
        assertEquals("Intro", text.substring(intro.range))
        val table = blocks[2] as Blocks.Table
        assertEquals(listOf(listOf("a", "b"), null, listOf("1", "2")), table.rows)
        assertEquals(1, table.headerRows)
        val code = blocks[3] as Blocks.Begin
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.inner)
        assertEquals("val x = 1", text.substring(code.innerStart, code.innerStart + code.inner.length))
        assertEquals("End", (blocks[4] as Blocks.Text).text)
    }

    @Test
    fun unclosedBlockIsText() {
        assertTrue(Blocks.parse("#+BEGIN_QUOTE\nno end").all { it is Blocks.Text })
        assertTrue(Blocks.showsNothing(":LOGBOOK:\nx\n:END:\n"))
        assertEquals(false, Blocks.showsNothing(":DETAILS:\nx\n:END:"))
    }
}
