package io.github.yighy.narwhal.data

import android.content.SharedPreferences
import org.json.JSONObject
import org.json.JSONArray
import android.content.Context
import io.github.yighy.narwhal.core.Agenda
import io.github.yighy.narwhal.core.Keywords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/** Light or dark appearance: as the system, or always one of them. */
enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
}

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
        /** Give every new note a CREATED property with when it was created. */
        val addCreatedToNewNotes: Boolean = false,
        /** Set reminders as alarm clock alarms (the most reliable; an alarm icon shows). */
        val useAlarmClock: Boolean = false,
        /** What happens to links still showing a note's old title when it is renamed. */
        val linkRenaming: LinkRenaming = LinkRenaming.Ask,
        val themeMode: ThemeMode = ThemeMode.System,
        /** Colors taken from the wallpaper (Android 12+). */
        val dynamicColor: Boolean = true,
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
            addCreatedToNewNotes = prefs.getBoolean(KEY_ADD_CREATED, false),
            useAlarmClock = prefs.getBoolean(KEY_ALARM_CLOCK, false),
            linkRenaming = LinkRenaming.entries.firstOrNull { it.name == prefs.getString(KEY_LINK_RENAMING, null) }
                ?: LinkRenaming.Ask,
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
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

    /** Saves a setting with [edit] and updates [values] with [change]. */
    private fun save(edit: SharedPreferences.Editor.() -> Unit, change: (Values) -> Values) {
        prefs.edit().apply(edit).apply()
        _values.update(change)
    }

    fun setShowContentInNotebook(show: Boolean) =
        save({ putBoolean(KEY_SHOW_CONTENT, show) }) { it.copy(showContentInNotebook = show) }

    fun setRemindScheduled(on: Boolean) = save({ putBoolean(KEY_REMIND_SCHEDULED, on) }) { it.copy(remindScheduled = on) }

    fun setRemindDeadline(on: Boolean) = save({ putBoolean(KEY_REMIND_DEADLINE, on) }) { it.copy(remindDeadline = on) }

    fun setRemindAllDay(on: Boolean) = save({ putBoolean(KEY_REMIND_ALL_DAY, on) }) { it.copy(remindAllDay = on) }

    fun setAllDayTime(time: LocalTime) =
        save({ putInt(KEY_ALL_DAY_MINUTES, time.hour * 60 + time.minute) }) { it.copy(allDayTime = time) }

    fun setAddIdToNewNotes(on: Boolean) = save({ putBoolean(KEY_ADD_ID, on) }) { it.copy(addIdToNewNotes = on) }

    fun setAddCreatedToNewNotes(on: Boolean) = save({ putBoolean(KEY_ADD_CREATED, on) }) { it.copy(addCreatedToNewNotes = on) }

    fun setUseAlarmClock(on: Boolean) = save({ putBoolean(KEY_ALARM_CLOCK, on) }) { it.copy(useAlarmClock = on) }

    fun setLinkRenaming(mode: LinkRenaming) = save({ putString(KEY_LINK_RENAMING, mode.name) }) { it.copy(linkRenaming = mode) }

    fun setThemeMode(mode: ThemeMode) = save({ putString(KEY_THEME, mode.name) }) { it.copy(themeMode = mode) }

    fun setDynamicColor(on: Boolean) = save({ putBoolean(KEY_DYNAMIC_COLOR, on) }) { it.copy(dynamicColor = on) }

    fun setKeywords(keywords: Keywords) = save({ putString(KEY_KEYWORDS, keywords.toSpec()) }) { it.copy(keywords = keywords) }

    companion object {
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_SEARCHES = "saved_searches"
        private const val ICON_PREFIX = "icon:"
        private const val KEY_ADD_ID = "add_id_to_new_notes"
        private const val KEY_ADD_CREATED = "add_created_to_new_notes"
        private const val KEY_ALARM_CLOCK = "use_alarm_clock"
        private const val KEY_LINK_RENAMING = "link_renaming"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_SHOW_CONTENT = "show_content_in_notebook"
        private const val KEY_REMIND_SCHEDULED = "remind_scheduled"
        private const val KEY_REMIND_DEADLINE = "remind_deadline"
        private const val KEY_REMIND_ALL_DAY = "remind_all_day"
        private const val KEY_ALL_DAY_MINUTES = "all_day_minutes"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
