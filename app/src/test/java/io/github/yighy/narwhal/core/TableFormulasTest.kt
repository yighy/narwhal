package io.github.yighy.narwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TableFormulasTest {

    private val table = listOf(
        "| Item | Qty | Price | Total |",
        "|------+-----+-------+-------|",
        "| Milk |   2 |  1.20 |       |",
        "| Bread |  1 |  2.50 |       |",
        "|------+-----+-------+-------|",
        "| Total |    |       |       |",
    )

    @Test
    fun columnAndFieldFormulas() {
        val formulas = TableFormulas.formulas(listOf("#+TBLFM: \$4=\$2*\$3;%.2f::@>\$4=vsum(@I..@II);%.2f"))
        val result = TableFormulas.recalculate(table, formulas)
        assertEquals(0, result.skipped)
        assertEquals(
            listOf(
                "| Item  | Qty | Price | Total |",
                "|-------+-----+-------+-------|",
                "| Milk  |   2 |  1.20 |  2.40 |",
                "| Bread |   1 |  2.50 |  2.50 |",
                "|-------+-----+-------+-------|",
                "| Total |     |       |  4.90 |",
            ),
            result.lines,
        )
    }

    @Test
    fun relativeReferencesAndFunctions() {
        val lines = listOf("| a | b |", "|---+---|", "| 1 |   |", "| 2 |   |", "| 6 |   |")
        val result = TableFormulas.recalculate(lines, listOf("\$2=\$1*2+1", "@>\$2=vmean(@2\$1..@-1\$1)"))
        assertEquals(listOf("| a |   b |", "|---+-----|", "| 1 |   3 |", "| 2 |   5 |", "| 6 | 1.5 |"), result.lines)
    }

    @Test
    fun unsupportedFormulasAreSkipped() {
        val lines = listOf("| 1 | 2 |")
        val result = TableFormulas.recalculate(lines, listOf("\$2='(+ \$1 1)", "\$2=sin(\$1)"))
        assertEquals(2, result.skipped)
        assertEquals(lines, result.lines)
    }

    @Test
    fun remoteReadsANamedTable() {
        val budget = listOf("| Item | Cost |", "|------+------|", "| A    |   10 |", "| B    |   15 |", "| Sum  |   25 |")
        val lines = listOf("| Total |   |")
        val result = TableFormulas.recalculate(lines, listOf("@1\$2=remote(budget, @>\$2)*2"), remote = { if (it == "budget") budget else null })
        assertEquals(listOf("| Total | 50 |"), result.lines)
        assertEquals(1, TableFormulas.recalculate(lines, listOf("@1\$2=remote(nope, @1\$1)")).skipped)
    }

    @Test
    fun namedTableLines() {
        val content = "Intro\n#+NAME: budget\n| a | 1 |\n#+TBLFM: \$2=1"
        assertEquals(listOf("| a | 1 |"), Blocks.tableLines(content, "budget"))
        val table = Blocks.parse(content)[1] as Blocks.Table
        assertEquals("budget", table.name)
        assertEquals(content.indexOf("| a"), table.tableStart)
    }
}
