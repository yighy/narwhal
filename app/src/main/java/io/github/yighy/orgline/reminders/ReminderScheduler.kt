package io.github.yighy.orgline.reminders

import io.github.yighy.orgline.ui.MainActivity
import org.json.JSONObject
import org.json.JSONArray
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.yighy.orgline.core.Agenda
import io.github.yighy.orgline.core.OrgNote
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps a single alarm set for the next reminder. When it fires, [ReminderReceiver] shows
 * everything that became due since the last check and sets the following alarm.
 */
class ReminderScheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /** Last time reminders were shown; reminders before it are never shown again. */
    private var lastCheck: LocalDateTime
        get() = prefs.getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0 }?.let(::toLocal) ?: now().also { lastCheck = it }
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, toMillis(value)).apply()

    /** True when the system lets the app set exact alarms (otherwise reminders may be late). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * Sets the alarm for the next reminder or snoozed one; as an alarm clock alarm with
     * [alarmClock] (the system then never delays it, and shows an alarm icon).
     */
    @Synchronized
    fun reschedule(notes: List<OrgNote>, options: Agenda.ReminderOptions, alarmClock: Boolean) {
        val intent = alarmIntent()
        alarms.cancel(intent)
        // Never schedule before the last check, so nothing is shown twice
        val after = maxOf(now(), lastCheck)
        val next = listOfNotNull(
            Agenda.nextReminder(notes, after, options),
            snoozes().map { it.at }.filter { it > after }.minOrNull(),
        ).minOrNull() ?: return
        val at = toMillis(next)
        when {
            alarmClock && canScheduleExact() -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent()), intent)
            canScheduleExact() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** A reminder put off by the notification's snooze: shown again at [at]. */
    data class Snooze(val fileUri: String, val title: String, val kind: Agenda.Kind, val at: LocalDateTime)

    @Synchronized
    fun snooze(snooze: Snooze) = saveSnoozes(snoozes() + snooze)

    private fun snoozes(): List<Snooze> = runCatching {
        val json = JSONArray(prefs.getString(KEY_SNOOZES, null) ?: return emptyList())
        List(json.length()) { i ->
            json.getJSONObject(i).let {
                Snooze(it.getString("uri"), it.getString("title"), Agenda.Kind.valueOf(it.getString("kind")), toLocal(it.getLong("at")))
            }
        }
    }.getOrDefault(emptyList())

    private fun saveSnoozes(snoozes: List<Snooze>) {
        val json = JSONArray()
        snoozes.forEach {
            json.put(JSONObject().put("uri", it.fileUri).put("title", it.title).put("kind", it.kind.name).put("at", toMillis(it.at)))
        }
        prefs.edit().putString(KEY_SNOOZES, json.toString()).apply()
    }

    /** Opens the app from the alarm icon of the status bar. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Reminders due since the last check (at most a day back, e.g. after the phone was off),
     * and marks them as shown.
     */
    @Synchronized
    fun takeDue(notes: List<OrgNote>, options: Agenda.ReminderOptions): List<Agenda.Reminder> {
        val now = now()
        val from = maxOf(lastCheck, now.minusDays(1))
        lastCheck = now
        // Snoozed reminders now due, for notes still there and not done
        val (due, later) = snoozes().partition { it.at <= now }
        if (due.isNotEmpty()) saveSnoozes(later)
        val snoozed = due.mapNotNull { s ->
            notes.firstOrNull { it.fileUri == s.fileUri && it.title == s.title && !it.isDone }?.let { Agenda.Reminder(it, s.kind, s.at) }
        }
        return Agenda.reminders(notes, from, now, options) + snoozed
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun now() = LocalDateTime.now().withNano(0)

    private fun toMillis(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun toLocal(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SNOOZES = "snoozes"
    }
}
