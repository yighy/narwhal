package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TableChangeTest {

    @Test
    fun rowBelow() {
        val text = "Stock:\n| Item | Qty |\n|------+-----|\n| Rice |   2 |\n| Eggs |  12 |\nafter"
        assertEquals(
            "Stock:\n| Item | Qty |\n|------+-----|\n| Rice |   2 |\n|      |     |\n| Eggs |  12 |\nafter",
            Blocks.withRowBelow(text, table = 0, line = 2),
        )
        // Under the last row
        assertEquals(
            "Stock:\n| Item | Qty |\n|------+-----|\n| Rice |   2 |\n| Eggs |  12 |\n|      |     |\nafter",
            Blocks.withRowBelow(text, table = 0, line = 3),
        )
    }

    @Test
    fun formulasFollow() {
        val text = "| a | b |\n| 1 | 2 |\n| 4 | 5 |\n#+TBLFM: @3$2=remote(other, @2$1)+@2$1::@>$1=vsum(@1..@3)\nafter"
        // A row under the first: @2 becomes @3, @3 becomes @4; remote()'s own reference stays
        assertEquals(
            "#+TBLFM: @4$2=remote(other, @2$1)+@3$1::@>$1=vsum(@1..@4)\nafter",
            Blocks.withRowBelow(text, table = 0, line = 0).substringAfterLast("|\n"),
        )
    }
}
