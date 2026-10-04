package io.github.yighy.orgline.reminders

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

    @Synchronized
    fun reschedule(notes: List<OrgNote>, options: Agenda.ReminderOptions) {
        val intent = alarmIntent()
        alarms.cancel(intent)
        // Never schedule before the last check, so nothing is shown twice
        val after = maxOf(now(), lastCheck)
        val next = Agenda.nextReminder(notes, after, options) ?: return
        val at = toMillis(next)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /**
     * Reminders due since the last check (at most a day back, e.g. after the phone was off),
     * and marks them as shown.
     */
    @Synchronized
    fun takeDue(notes: List<OrgNote>, options: Agenda.ReminderOptions): List<Agenda.Reminder> {
        val now = now()
        val from = maxOf(lastCheck, now.minusDays(1))
        lastCheck = now
        return Agenda.reminders(notes, from, now, options)
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
    }
}
