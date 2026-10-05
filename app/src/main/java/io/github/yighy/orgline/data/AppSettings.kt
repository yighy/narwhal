package io.github.yighy.orgline.data

import org.json.JSONObject
import org.json.JSONArray
import android.content.Context
import io.github.yighy.orgline.core.Agenda
import io.github.yighy.orgline.core.Keywords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/** When a note is renamed, links to it that still show its old title can follow. */
enum class LinkRenaming(val label: String) {
    Ask("Ask each time"),
    Always("Update them"),
    Never("Leave them as they are"),
}

/** A search kept under a name, run from the drawer (as Orgzly's saved searches). */
data class SavedSearch(val name: String, val query: String)

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
        /** What happens to links still showing a note's old title when it is renamed. */
        val linkRenaming: LinkRenaming = LinkRenaming.Ask,
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
            linkRenaming = LinkRenaming.entries.firstOrNull { it.name == prefs.getString(KEY_LINK_RENAMING, null) }
                ?: LinkRenaming.Ask,
        ),
    )
    val values: StateFlow<Values> = _values

    /** Emoji chosen as a notebook's icon, by file URI; kept in the app, not in the files. */
    private val _notebookIcons = MutableStateFlow(
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(ICON_PREFIX) && value is String) key.removePrefix(ICON_PREFIX) to value else null
        }.toMap(),
    )
    val notebookIcons: StateFlow<Map<String, String>> = _notebookIcons

    /** Sets the notebook's icon, or goes back to its initial with null. */
    fun setNotebookIcon(fileUri: String, emoji: String?) {
        prefs.edit().apply { if (emoji == null) remove(ICON_PREFIX + fileUri) else putString(ICON_PREFIX + fileUri, emoji) }.apply()
        _notebookIcons.update { if (emoji == null) it - fileUri else it + (fileUri to emoji) }
    }

    private val _savedSearches = MutableStateFlow(readSearches())
    val savedSearches: StateFlow<List<SavedSearch>> = _savedSearches

    fun setSavedSearches(searches: List<SavedSearch>) {
        val json = JSONArray()
        searches.forEach { json.put(JSONObject().put("name", it.name).put("query", it.query)) }
        prefs.edit().putString(KEY_SEARCHES, json.toString()).apply()
        _savedSearches.value = searches
    }

    private fun readSearches(): List<SavedSearch> = runCatching {
        val json = JSONArray(prefs.getString(KEY_SEARCHES, null) ?: return emptyList())
        List(json.length()) { i -> json.getJSONObject(i).let { SavedSearch(it.getString("name"), it.getString("query")) } }
    }.getOrDefault(emptyList())

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

    fun setLinkRenaming(mode: LinkRenaming) {
        prefs.edit().putString(KEY_LINK_RENAMING, mode.name).apply()
        _values.update { it.copy(linkRenaming = mode) }
    }

    fun setKeywords(keywords: Keywords) {
        prefs.edit().putString(KEY_KEYWORDS, keywords.toSpec()).apply()
        _values.update { it.copy(keywords = keywords) }
    }

    companion object {
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_SEARCHES = "saved_searches"
        private const val ICON_PREFIX = "icon:"
        private const val KEY_ADD_ID = "add_id_to_new_notes"
        private const val KEY_LINK_RENAMING = "link_renaming"
        private const val KEY_SHOW_CONTENT = "show_content_in_notebook"
        private const val KEY_REMIND_SCHEDULED = "remind_scheduled"
        private const val KEY_REMIND_DEADLINE = "remind_deadline"
        private const val KEY_REMIND_ALL_DAY = "remind_all_day"
        private const val KEY_ALL_DAY_MINUTES = "all_day_minutes"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
