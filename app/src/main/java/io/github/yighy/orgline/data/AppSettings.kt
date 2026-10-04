package io.github.yighy.orgline.data

import android.content.Context
import io.github.yighy.orgline.core.Agenda
import io.github.yighy.orgline.core.Keywords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/** User preferences, stored in SharedPreferences. */
class AppSettings(context: Context) {

    data class Values(
        /** Show each note's content under its title in the notebook outline. */
        val showContentInNotebook: Boolean = false,
        val remindScheduled: Boolean = true,
        val remindDeadline: Boolean = true,
        /** Also remind for dates without a time of day, at [allDayTime]. */
        val remindAllDay: Boolean = false,
        val allDayTime: LocalTime = LocalTime.of(9, 0),
        /** State keywords for files without a #+TODO line. */
        val keywords: Keywords = Keywords.DEFAULT,
        /** Give every new note an ID property, so it can be linked to with id:. */
        val addIdToNewNotes: Boolean = false,
    ) {
        val reminderOptions: Agenda.ReminderOptions
            get() = Agenda.ReminderOptions(remindScheduled, remindDeadline, allDayTime.takeIf { remindAllDay })
    }

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _values = MutableStateFlow(
        Values(
            showContentInNotebook = prefs.getBoolean(KEY_SHOW_CONTENT, false),
            remindScheduled = prefs.getBoolean(KEY_REMIND_SCHEDULED, true),
            remindDeadline = prefs.getBoolean(KEY_REMIND_DEADLINE, true),
            remindAllDay = prefs.getBoolean(KEY_REMIND_ALL_DAY, false),
            allDayTime = LocalTime.ofSecondOfDay(prefs.getInt(KEY_ALL_DAY_MINUTES, 9 * 60) * 60L),
            keywords = prefs.getString(KEY_KEYWORDS, null)?.let(Keywords::parse) ?: Keywords.DEFAULT,
            addIdToNewNotes = prefs.getBoolean(KEY_ADD_ID, false),
        ),
    )
    val values: StateFlow<Values> = _values

    /** Whether the app already asked for the notification permission once. */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, value).apply()

    fun setShowContentInNotebook(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CONTENT, show).apply()
        _values.update { it.copy(showContentInNotebook = show) }
    }

    fun setRemindScheduled(on: Boolean) {
        prefs.edit().putBoolean(KEY_REMIND_SCHEDULED, on).apply()
        _values.update { it.copy(remindScheduled = on) }
    }

    fun setRemindDeadline(on: Boolean) {
        prefs.edit().putBoolean(KEY_REMIND_DEADLINE, on).apply()
        _values.update { it.copy(remindDeadline = on) }
    }

    fun setRemindAllDay(on: Boolean) {
        prefs.edit().putBoolean(KEY_REMIND_ALL_DAY, on).apply()
        _values.update { it.copy(remindAllDay = on) }
    }

    fun setAllDayTime(time: LocalTime) {
        prefs.edit().putInt(KEY_ALL_DAY_MINUTES, time.hour * 60 + time.minute).apply()
        _values.update { it.copy(allDayTime = time) }
    }

    fun setAddIdToNewNotes(on: Boolean) {
        prefs.edit().putBoolean(KEY_ADD_ID, on).apply()
        _values.update { it.copy(addIdToNewNotes = on) }
    }

    fun setKeywords(keywords: Keywords) {
        prefs.edit().putString(KEY_KEYWORDS, keywords.toSpec()).apply()
        _values.update { it.copy(keywords = keywords) }
    }

    companion object {
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_ADD_ID = "add_id_to_new_notes"
        private const val KEY_SHOW_CONTENT = "show_content_in_notebook"
        private const val KEY_REMIND_SCHEDULED = "remind_scheduled"
        private const val KEY_REMIND_DEADLINE = "remind_deadline"
        private const val KEY_REMIND_ALL_DAY = "remind_all_day"
        private const val KEY_ALL_DAY_MINUTES = "all_day_minutes"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
