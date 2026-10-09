package io.github.yighy.narwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ListFoldsTest {

    @Test
    fun itemsWithSubItemsFold() {
        val text = "Intro\n- Fruits\n  - Apple\n  - Pear\n- Bread\nEnd"
        val parts = ListFolds.parse(text)
        assertEquals(3, parts.size)
        assertEquals(ListFolds.Plain(0, "Intro"), parts[0])
        val fruits = parts[1] as ListFolds.Item
        assertEquals("- Fruits", fruits.line)
        assertEquals("  - Apple\n  - Pear", fruits.children)
        assertEquals(fruits.children, text.substring(fruits.childrenStart, fruits.childrenStart + fruits.children.length))
        assertEquals(ListFolds.Plain(text.indexOf("- Bread"), "- Bread\nEnd"), parts[2])
    }

    @Test
    fun flatListDoesNotFold() {
        assertFalse(ListFolds.hasFolds("- a\n- b\n  \n- c"))
        // Text going on under an item is not a sub-item
        assertFalse(ListFolds.hasFolds("- a long item\n  that goes on\n- b"))
    }
}
