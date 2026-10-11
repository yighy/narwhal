package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharingTest {

    @Test
    fun pageTitleAndLink() {
        assertEquals(Sharing.Shared("A page", "https://example.org"), Sharing.note("A page", "https://example.org"))
    }

    @Test
    fun firstLineAsTitle() {
        assertEquals(Sharing.Shared("Buy", "milk\nbread"), Sharing.note(null, "Buy\nmilk\nbread"))
        assertEquals(Sharing.Shared("https://example.org", ""), Sharing.note("", "https://example.org"))
        assertNull(Sharing.note(null, "  "))
    }
}
