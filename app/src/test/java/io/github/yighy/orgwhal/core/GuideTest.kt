package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class GuideTest {

    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun datesAndIdAreFilledIn() {
        assertEquals("<2026-10-12 Mon> [2026-10-09 Fri 09:00] id:X", Guide.fill("<{{date:+2}}> [{{date:-1}} 09:00] id:{{id}}", today, "X"))
    }

    @Test
    fun theGuideHasNoPlaceholderLeftAndValidDates() {
        val guide = Guide.fill(File("src/main/assets/${Guide.ASSET}").readText(), today, "ID-1")
        assertFalse(guide.contains("{{"))
        val notes = OrgNote.fromDocument(OrgDocument.parse(guide), "f", "${Guide.FILE_NAME}.org")
        // Its planning times are dates Org reads, without a time of day (no reminders by default)
        val planned = notes.flatMap { listOfNotNull(it.scheduled, it.deadline) }
        assertTrue(planned.isNotEmpty())
        planned.forEach { assertEquals(null, OrgTime.parts(it)!!.time) }
        // The example link leads to its target
        assertEquals("The target note", notes.single { it.id == "ID-1" }.title)
    }
}
