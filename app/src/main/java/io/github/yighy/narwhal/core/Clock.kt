package io.github.yighy.narwhal.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Org clocking in a note's content: CLOCK lines in its :LOGBOOK: drawer, such as
 * "CLOCK: [2026-10-09 Fri 10:00]--[2026-10-09 Fri 11:30] =>  1:30", or without the end while
 * the clock runs.
 */
object Clock {

    data class Entry(val start: LocalDateTime, val end: LocalDateTime?, val range: IntRange)

    private const val STAMP = """\[(\d{4})-(\d{2})-(\d{2})[^\]\d]*(\d{1,2}):(\d{2})]"""
    private val LINE = Regex("""^[ \t]*CLOCK:[ \t]*$STAMP(?:--$STAMP)?[^\n]*$""", RegexOption.MULTILINE)
    private val DRAWER = Regex("""^([ \t]*):LOGBOOK:[ \t]*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    fun entries(content: String): List<Entry> = LINE.findAll(content).mapNotNull { m ->
        val g = m.groupValues
        val start = time(g, 1) ?: return@mapNotNull null
        Entry(start, if (g[6].isEmpty()) null else time(g, 6), m.range)
    }.toList()

    /** The clock running in [content], if any. */
    fun running(content: String): Entry? = entries(content).firstOrNull { it.end == null }

    /** Time clocked in [content], the running clock counting up to [now]. */
    fun total(content: String, now: LocalDateTime): Duration =
        entries(content).fold(Duration.ZERO) { sum, e -> sum + Duration.between(e.start, e.end ?: now).coerceAtLeast(Duration.ZERO) }

    /** [content] with a running clock from [now], first in its logbook (made if needed). */
    fun clockIn(content: String, now: LocalDateTime): String {
        if (running(content) != null) return content
        val line = "CLOCK: " + OrgTime.inactive(now)
        val drawer = DRAWER.find(content)
        if (drawer != null) {
            val at = drawer.range.last + 1
            return content.substring(0, at) + "\n" + drawer.groupValues[1] + line + content.substring(at)
        }
        val rest = content.trimStart('\n')
        return ":LOGBOOK:\n$line\n:END:" + if (rest.isEmpty()) "" else "\n$rest"
    }

    /** [content] with its running clock stopped at [now]. */
    fun clockOut(content: String, now: LocalDateTime): String {
        val entry = running(content) ?: return content
        val line = content.substring(entry.range)
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val start = line.trim().removePrefix("CLOCK:").trim().substringBefore(']') + "]"
        val end = OrgTime.inactive(now.coerceAtLeast(entry.start))
        val stopped = "${indent}CLOCK: $start--$end =>  ${format(Duration.between(entry.start, now).coerceAtLeast(Duration.ZERO))}"
        return content.replaceRange(entry.range, stopped)
    }

    /** "1:05" as Org writes durations (hours, then minutes). */
    fun format(duration: Duration): String {
        val minutes = duration.toMinutes()
        return "%d:%02d".format(minutes / 60, minutes % 60)
    }

    private fun time(g: List<String>, from: Int): LocalDateTime? = runCatching {
        LocalDateTime.of(
            LocalDate.of(g[from].toInt(), g[from + 1].toInt(), g[from + 2].toInt()),
            LocalTime.of(g[from + 3].toInt(), g[from + 4].toInt()),
        )
    }.getOrNull()
}
