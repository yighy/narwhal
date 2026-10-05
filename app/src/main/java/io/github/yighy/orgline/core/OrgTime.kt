package io.github.yighy.orgline.core

import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgRange
import com.orgzly.org.datetime.OrgRepeater
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

/** Org timestamps for planning times (SCHEDULED, DEADLINE). */
object OrgTime {

    data class Parts(val date: LocalDate, val time: LocalTime?, val repeater: String?)

    /** OrgRange.parseOrNull still throws on text that is not a timestamp. */
    fun range(timestamp: String): OrgRange? = runCatching { OrgRange.parse(timestamp) }.getOrNull()

    fun parts(timestamp: String?): Parts? {
        val start = timestamp?.let(::range)?.startTime ?: return null
        val cal = start.calendar
        val date = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val time = if (start.hasTime()) LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)) else null
        return Parts(date, time, if (start.hasRepeater()) start.repeater.toString() else null)
    }

    /**
     * Builds an active timestamp such as "<2026-10-05 Mon 09:00 +1w>", keeping the warning
     * delay of [previous] if there was one. The [repeater] ("+1w", ".+2d", null for none)
     * is the previous one unless given.
     */
    fun build(
        date: LocalDate,
        time: LocalTime?,
        previous: String? = null,
        repeater: String? = parts(previous)?.repeater,
    ): String {
        val old = previous?.let(::range)?.startTime
        val builder = (if (old != null) OrgDateTime.Builder(old) else OrgDateTime.Builder())
            .setIsActive(true)
            .setYear(date.year)
            .setMonth(date.monthValue - 1)
            .setDay(date.dayOfMonth)
            .setHasTime(time != null)
            .setHasEndTime(false)
            .setRepeater(repeater?.let { runCatching { OrgRepeater.parse(it) }.getOrNull() })
        if (time != null) builder.setHour(time.hour).setMinute(time.minute)
        return OrgRange(builder.build()).toString()
    }

    /** A repeater split into its parts: "++2w" is ("++", 2, 'w'), "/4d" habit part kept in [rest]. */
    data class Repeat(val type: String, val every: Int, val unit: Char, val rest: String = "") {
        override fun toString() = "$type$every$unit$rest"

        /** E.g. "every week (+)", "every 2 days from done (.+)". */
        fun describe(): String {
            val name = UNITS.getValue(unit)
            val interval = if (every == 1) "every $name" else "every $every ${name}s"
            val from = if (type == ".+") " from done" else ""
            return "$interval$from ($type)"
        }

        companion object {
            private val UNITS = mapOf('h' to "hour", 'd' to "day", 'w' to "week", 'm' to "month", 'y' to "year")
            private val PATTERN = Regex("""^(\.\+|\+\+|\+)(\d+)([hdwmy])(/\d+[hdwmy])?$""")

            fun parse(text: String?): Repeat? {
                val m = text?.let { PATTERN.matchEntire(it.trim()) } ?: return null
                return Repeat(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3][0], m.groupValues[4])
            }
        }
    }

    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Human-readable form, e.g. "Mon, Oct 5, 2026 · 09:00 · every week (+)". */
    fun display(timestamp: String?): String? {
        val p = parts(timestamp) ?: return timestamp
        return buildList {
            add(p.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + ", " + p.date.format(dateFormat))
            p.time?.let { add(it.format(timeFormat)) }
            p.repeater?.let { add(Repeat.parse(it)?.describe() ?: "repeats $it") }
        }.joinToString(" · ")
    }
}
