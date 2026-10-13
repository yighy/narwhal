package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellEditTest {

    @Test
    fun setsTheFieldAndRealigns() {
        val text = "Stock:\n|Quantité|\n|-|\n|1|\nafter"
        val edited = Blocks.withCell(text, Blocks.CellEdit(table = 0, line = 2, column = 0, value = "12"))
        assertEquals("Stock:\n| Quantité |\n|----------|\n|       12 |\nafter", edited)
    }

    @Test
    fun keepsTheIndentAndOtherTables() {
        val text = "  | a | b |\n  | x | 1 |\n\n| c |\n| 2 |"
        val edited = Blocks.withCell(text, Blocks.CellEdit(table = 1, line = 1, column = 0, value = "30"))
        // A column of numbers aligns right, its text too, as in Emacs
        assertEquals("  | a | b |\n  | x | 1 |\n\n|  c |\n| 30 |", edited)
        val first = Blocks.withCell(text, Blocks.CellEdit(table = 0, line = 1, column = 0, value = "long"))
        assertTrue(first.startsWith("  | a    | b |\n  | long | 1 |\n"))
    }

    @Test
    fun escapesBarsAndNewLines() {
        val edited = Blocks.withCell("| a |", Blocks.CellEdit(0, 0, 0, " x|y\nz "))
        assertEquals("| x\\verty z |", edited)
    }

    @Test
    fun missingTableLeavesTheText() {
        assertEquals("no table", Blocks.withCell("no table", Blocks.CellEdit(0, 0, 0, "1")))
    }

    @Test
    fun computedCells() {
        val lines = listOf("| a | b | c |", "|---+---+---|", "| 1 | 2 | 3 |", "| 4 | 5 | 9 |")
        // A column formula covers the body, a field formula its field
        assertEquals(setOf(2 to 2, 3 to 2), TableFormulas.computedCells(lines, listOf("$3=$1+$2")))
        assertEquals(setOf(3 to 0), TableFormulas.computedCells(lines, listOf("@>$1=vsum(@I..@-1)")))
        // Unknown targets count for nothing
        assertEquals(emptySet<Pair<Int, Int>>(), TableFormulas.computedCells(lines, listOf("\$total=1", "x")))
    }

    @Test
    fun numbers() {
        assertTrue(TableFormulas.isNumber(" 9 "))
        assertTrue(TableFormulas.isNumber("-1.50"))
        assertFalse(TableFormulas.isNumber("abc"))
        assertFalse(TableFormulas.isNumber(""))
    }
}
