package io.github.yighy.orgwhal.core

import io.github.yighy.orgwhal.core.Blocks.TableChange
import org.junit.Assert.assertEquals
import org.junit.Test

class TableChangeTest {

    private val table = "Stock:\n| Item | Qty |\n|------+-----|\n| Rice |   2 |\n| Eggs |  12 |\nafter"

    private fun change(text: String, line: Int, column: Int, change: TableChange) =
        Blocks.withTableChange(text, table = 0, line = line, column = column, change = change)

    @Test
    fun rows() {
        assertEquals(
            "Stock:\n| Item | Qty |\n|------+-----|\n| Rice |   2 |\n|      |     |\n| Eggs |  12 |\nafter",
            change(table, 2, 0, TableChange.RowBelow),
        )
        assertEquals(
            "Stock:\n| Item | Qty |\n|------+-----|\n|      |     |\n| Rice |   2 |\n| Eggs |  12 |\nafter",
            change(table, 2, 1, TableChange.RowAbove),
        )
        // Under the last row
        assertEquals(
            "Stock:\n| Item | Qty |\n|------+-----|\n| Rice |   2 |\n| Eggs |  12 |\n|      |     |\nafter",
            change(table, 3, 0, TableChange.RowBelow),
        )
    }

    @Test
    fun columns() {
        // An empty column is as wide as one character, as in Emacs
        assertEquals(
            "Stock:\n| Item |   | Qty |\n|------+---+-----|\n| Rice |   |   2 |\n| Eggs |   |  12 |\nafter",
            change(table, 2, 0, TableChange.ColumnRight),
        )
        assertEquals(
            "Stock:\n|   | Item | Qty |\n|---+------+-----|\n|   | Rice |   2 |\n|   | Eggs |  12 |\nafter",
            change(table, 2, 0, TableChange.ColumnLeft),
        )
    }

    @Test
    fun deletions() {
        assertEquals("Stock:\n| Item | Qty |\n|------+-----|\n| Eggs |  12 |\nafter", change(table, 2, 0, TableChange.DeleteRow))
        assertEquals("Stock:\n| Qty |\n|-----|\n|   2 |\n|  12 |\nafter", change(table, 3, 0, TableChange.DeleteColumn))
        // The references after a row or column taken out move back
        val text = "| a | b | c |\n| 1 | 2 | 3 |\n| 4 | 5 | 9 |\n#+TBLFM: $3=$1+$2::@3$1=@2$1\nafter"
        // The second row taken out: @3 becomes @2
        assertEquals("#+TBLFM: $3=$1+$2::@2$1=@2$1\nafter", change(text, 1, 0, TableChange.DeleteRow).substringAfterLast("|\n"))
        // The first column taken out: $2 becomes $1, $3 becomes $2
        assertEquals("#+TBLFM: $2=$1+$1::@3$1=@2$1\nafter", change(text, 1, 0, TableChange.DeleteColumn).substringAfterLast("|\n"))
    }

    @Test
    fun formulasFollow() {
        val text = "| a | b |\n| 1 | 2 |\n| 4 | 5 |\n#+TBLFM: @3$2=remote(other, @2$1)+@2$1::@>$1=vsum(@1..@3)\nafter"
        // A row under the first: @2 becomes @3, @3 becomes @4; remote()'s own reference stays
        assertEquals(
            "#+TBLFM: @4$2=remote(other, @2$1)+@3$1::@>$1=vsum(@1..@4)\nafter",
            change(text, 0, 0, TableChange.RowBelow).substringAfterLast("|\n"),
        )
        // A column left of the first: every $n moves on, after a row (@3$2) as well
        assertEquals(
            "#+TBLFM: @3$3=remote(other, @2$1)+@2$2::@>$2=vsum(@1..@3)\nafter",
            change(text, 0, 0, TableChange.ColumnLeft).substringAfterLast("|\n"),
        )
        // A column right of the first: only those after it
        assertEquals(
            "#+TBLFM: @3$3=remote(other, @2$1)+@2$1::@>$1=vsum(@1..@3)\nafter",
            change(text, 0, 0, TableChange.ColumnRight).substringAfterLast("|\n"),
        )
    }
}
