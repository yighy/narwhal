package io.github.yighy.orgline.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yighy.orgline.OrglineApplication
import io.github.yighy.orgline.core.Keywords
import io.github.yighy.orgline.core.NoteStates
import kotlinx.coroutines.launch

/**
 * Shows due reminders when the alarm fires, marks a note done from its notification, and
 * sets the alarm again after a reboot, an app update or a clock change.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OrglineApplication
        val pending = goAsync()
        app.scope.launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: OrglineApplication, intent: Intent) {
        val repository = app.repository
        repository.refresh()
        val library = repository.library.value
        if (!library.loaded) return
        val options = app.settings.values.value.reminderOptions

        when (intent.action) {
            ACTION_DONE -> {
                val uri = intent.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
                val index = intent.getIntExtra(ReminderNotifications.EXTRA_INDEX, -1)
                val title = intent.getStringExtra(ReminderNotifications.EXTRA_TITLE)
                ReminderNotifications.cancel(app, intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0))
                // Same position and title as when notified, so a moved note is not touched
                val note = library.note(uri, index)?.takeIf { it.title == title } ?: return
                val keywords = library.file(uri)?.doc?.keywords ?: Keywords.DEFAULT
                val target = keywords.done.firstOrNull() ?: return
                runCatching { repository.saveNote(note, NoteStates.apply(note.toEdit(), target, keywords).edit) }
            }

            // Alarm, or boot / update / clock change: also shows reminders missed meanwhile
            else -> {
                app.scheduler.takeDue(library.notes, options).forEach { reminder ->
                    val notebook = library.file(reminder.note.fileUri)?.title ?: reminder.note.fileName
                    ReminderNotifications.show(app, reminder, notebook)
                }
            }
        }

        // Boot, update, clock change, or after showing: set the next alarm
        app.scheduler.reschedule(repository.library.value.notes, options)
    }

    companion object {
        const val ACTION_REMIND = "io.github.yighy.orgline.action.REMIND"
        const val ACTION_DONE = "io.github.yighy.orgline.action.DONE"
    }
}
