package io.github.yighy.orgline.core

import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgRange
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
     * Builds an active timestamp such as "<2026-10-05 Mon 09:00>", keeping the repeater
     * and warning delay of [previous] if there was one.
     */
    fun build(date: LocalDate, time: LocalTime?, previous: String? = null): String {
        val old = previous?.let(::range)?.startTime
        val builder = (if (old != null) OrgDateTime.Builder(old) else OrgDateTime.Builder())
            .setIsActive(true)
            .setYear(date.year)
            .setMonth(date.monthValue - 1)
            .setDay(date.dayOfMonth)
            .setHasTime(time != null)
            .setHasEndTime(false)
        if (time != null) builder.setHour(time.hour).setMinute(time.minute)
        return OrgRange(builder.build()).toString()
    }

    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Human-readable form, e.g. "Mon, Oct 5, 2026 · 09:00 · repeats +1w". */
    fun display(timestamp: String?): String? {
        val p = parts(timestamp) ?: return timestamp
        return buildList {
            add(p.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + ", " + p.date.format(dateFormat))
            p.time?.let { add(it.format(timeFormat)) }
            p.repeater?.let { add("repeats $it") }
        }.joinToString(" · ")
    }
}
