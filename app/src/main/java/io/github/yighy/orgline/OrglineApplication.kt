package io.github.yighy.orgline

import android.app.Application
import io.github.yighy.orgline.data.AppSettings
import io.github.yighy.orgline.data.NoteRepository
import io.github.yighy.orgline.reminders.ReminderNotifications
import io.github.yighy.orgline.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Holds the app-wide repository and settings, shared by the UI and the reminder receiver,
 * and keeps the next reminder alarm in sync with the notes.
 */
class OrglineApplication : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository by lazy { NoteRepository(this) }
    val settings by lazy { AppSettings(this) }
    val scheduler by lazy { ReminderScheduler(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)

        // Reschedule the next alarm whenever the notes or the reminder settings change
        scope.launch {
            combine(repository.library.filter { it.loaded }, settings.values) { library, values ->
                library.notes to values.reminderOptions
            }
                .distinctUntilChanged()
                .debounce(500)
                .collect { (notes, options) -> scheduler.reschedule(notes, options) }
        }
    }
}
