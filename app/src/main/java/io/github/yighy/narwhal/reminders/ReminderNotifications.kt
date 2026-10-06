package io.github.yighy.narwhal.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.yighy.narwhal.NarwhalApplication
import io.github.yighy.narwhal.R
import io.github.yighy.narwhal.data.AppSettings
import io.github.yighy.narwhal.core.Agenda
import io.github.yighy.narwhal.ui.MainActivity
import io.github.yighy.narwhal.ui.displayTitle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object ReminderNotifications {

    private const val CHANNEL_ID = "reminders"

    const val EXTRA_FILE_URI = "file_uri"
    const val EXTRA_INDEX = "index"
    const val EXTRA_TITLE = "title"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Scheduled and deadline times of your notes"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, reminder: Agenda.Reminder, notebook: String) {
        if (!canPost(context)) return
        val note = reminder.note
        val id = notificationId(note.fileUri, note.index, reminder.kind)

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putNoteExtras(note.fileUri, note.index, note.title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_DONE)
                .putNoteExtras(note.fileUri, note.index, note.title)
                .putExtra(EXTRA_NOTIFICATION_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Snoozes for [minutes]; each its own request code, so both buttons work. */
        fun snooze(minutes: Long, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_SNOOZE)
                .putNoteExtras(note.fileUri, note.index, note.title)
                .putExtra(EXTRA_KIND, reminder.kind.name)
                .putExtra(EXTRA_SNOOZE_MINUTES, minutes)
                .putExtra(EXTRA_NOTIFICATION_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val snoozeMinutes = (context.applicationContext as NarwhalApplication).settings.values.value.snoozeMinutes
        val kind = if (reminder.kind == Agenda.Kind.Deadline) "Deadline" else "Scheduled"
        val time = reminder.at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(listOfNotNull(note.state, displayTitle(note.title)).joinToString(" "))
            .setContentText("$kind $time · $notebook")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Done", done)
            .addAction(0, AppSettings.snoozeLabel(snoozeMinutes), snooze(snoozeMinutes.toLong(), id + 1))
            .addAction(0, "Tomorrow", snooze(24 * 60, id + 2))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked in the meantime
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun notificationId(fileUri: String, index: Int, kind: Agenda.Kind) = "$fileUri#$index#$kind".hashCode()

    private fun Intent.putNoteExtras(fileUri: String, index: Int, title: String) = this
        .putExtra(EXTRA_FILE_URI, fileUri)
        .putExtra(EXTRA_INDEX, index)
        .putExtra(EXTRA_TITLE, title)
}
