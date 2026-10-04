package io.github.yighy.orgline.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yighy.orgline.core.Keywords
import io.github.yighy.orgline.core.NoteEdit
import io.github.yighy.orgline.core.NoteStates
import io.github.yighy.orgline.core.NoteTree
import io.github.yighy.orgline.core.OrgTime
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.OrglineApplication
import io.github.yighy.orgline.data.NoteRepository
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Notebooks : Screen
    data class Notebook(val fileUri: String) : Screen
    data class Note(val fileUri: String, val index: Int) : Screen
    data object Settings : Screen
    data object Agenda : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val orgline = app as OrglineApplication
    private val repository = orgline.repository
    val library = repository.library

    private val appSettings = orgline.settings
    val settings = appSettings.values

    val canScheduleExactAlarms: Boolean get() = orgline.scheduler.canScheduleExact()

    var askedNotificationPermission: Boolean
        get() = appSettings.askedNotificationPermission
        set(value) {
            appSettings.askedNotificationPermission = value
        }

    /** True while a pull-to-refresh is running. */
    var refreshing by mutableStateOf(false)
        private set

    val stack = mutableStateListOf<Screen>(Screen.Notebooks)

    /** Direction of the last navigation, for the screen transition. */
    var navigatedForward by mutableStateOf(true)
        private set

    var searchActive by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var tag by mutableStateOf<String?>(null)

    fun setSearch(active: Boolean) {
        searchActive = active
        if (!active) {
            query = ""
            tag = null
        }
    }

    /** Folded notes in the notebook outline, by "fileUri#index". */
    private val folded = mutableStateMapOf<String, Boolean>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun pullToRefresh() {
        refreshing = true
        viewModelScope.launch {
            repository.refresh()
            refreshing = false
        }
    }

    fun setShowContentInNotebook(show: Boolean) = appSettings.setShowContentInNotebook(show)
    fun setRemindScheduled(on: Boolean) = appSettings.setRemindScheduled(on)
    fun setRemindDeadline(on: Boolean) = appSettings.setRemindDeadline(on)
    fun setRemindAllDay(on: Boolean) = appSettings.setRemindAllDay(on)
    fun setAllDayTime(time: LocalTime) = appSettings.setAllDayTime(time)

    fun openSettings() = push(Screen.Settings)

    fun openAgenda() {
        if (stack.last() != Screen.Agenda) push(Screen.Agenda)
    }

    /** Note to open once the notes are loaded (from a reminder notification). */
    private data class PendingOpen(val fileUri: String, val index: Int, val title: String?)
    private var pendingOpen: PendingOpen? = null

    fun openFromNotification(fileUri: String, index: Int, title: String?) {
        pendingOpen = PendingOpen(fileUri, index, title)
        openPendingNote()
    }

    /** Opens the note requested by a notification, if the notes are loaded. */
    fun openPendingNote() {
        val p = pendingOpen ?: return
        val lib = library.value
        if (!lib.loaded) return
        pendingOpen = null
        val note = lib.note(p.fileUri, p.index)?.takeIf { p.title == null || it.title == p.title }
            ?: lib.file(p.fileUri)?.notes?.firstOrNull { it.title == p.title }
        if (note != null) openNote(note) else message("This note has moved or was deleted")
    }

    fun setFolder(uri: Uri) {
        viewModelScope.launch { repository.setFolder(uri) }
        stack.clear()
        stack.add(Screen.Notebooks)
    }

    fun isFolded(note: OrgNote) = folded[key(note)] == true

    fun toggleFold(note: OrgNote) {
        folded[key(note)] = !isFolded(note)
    }

    /** Folds (or unfolds) every note that has children. */
    fun setAllFolded(notes: List<OrgNote>, fold: Boolean) {
        notes.forEachIndexed { i, note ->
            if (NoteTree.hasChildren(notes, i)) folded[key(note)] = fold
        }
    }

    private fun key(note: OrgNote) = note.fileUri + "#" + note.index

    private fun push(screen: Screen) {
        navigatedForward = true
        stack.add(screen)
    }

    fun openHome() {
        navigatedForward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Goes back to the notebook if it is already open below, otherwise opens it. */
    fun openNotebook(fileUri: String) {
        val existing = stack.indexOfLast { it is Screen.Notebook && it.fileUri == fileUri }
        if (existing >= 0) {
            navigatedForward = false
            while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
        } else {
            setSearch(false)
            push(Screen.Notebook(fileUri))
        }
    }

    fun openNote(note: OrgNote) = push(Screen.Note(note.fileUri, note.index))

    fun openById(id: String) {
        val note = library.value.noteById(id)
        if (note != null) openNote(note) else message("No note with ID $id")
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        navigatedForward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Returns the saved note, or null if it could not be saved. */
    suspend fun save(note: OrgNote, edit: NoteEdit): OrgNote? = attempt { repository.saveNote(note, edit) }

    fun update(note: OrgNote, change: (NoteEdit) -> NoteEdit) {
        viewModelScope.launch { save(note, change(note.toEdit())) }
    }

    /** Changes the state with Org semantics (CLOSED time, repeating tasks moving forward). */
    fun setState(note: OrgNote, target: String?) {
        val keywords = library.value.file(note.fileUri)?.doc?.keywords ?: Keywords.DEFAULT
        val result = NoteStates.apply(note.toEdit(), target, keywords)
        viewModelScope.launch {
            val saved = save(note, result.edit) ?: return@launch
            if (result.shifted) {
                val next = OrgTime.display(saved.scheduled ?: saved.deadline)
                message("Repeating task moved to $next")
            }
        }
    }

    fun toggleDone(note: OrgNote) {
        val keywords = library.value.file(note.fileUri)?.doc?.keywords ?: Keywords.DEFAULT
        setState(note, NoteStates.toggleTarget(note, keywords))
    }

    /** Outline row whose swipe actions are shown, by "fileUri#index"; only one at a time. */
    var openSwipeRow by mutableStateOf<String?>(null)

    /** Returns the target's ID (created if needed) and the target as now stored. */
    suspend fun idFor(target: OrgNote): Pair<String, OrgNote>? = attempt { repository.ensureId(target) }

    fun createNotebook(name: String) {
        viewModelScope.launch {
            attempt { openNotebook(repository.createFile(name).toString()) }
        }
    }

    fun setPreface(file: NoteRepository.LoadedFile, newPreface: String) {
        viewModelScope.launch {
            attempt { repository.setPreface(file.uri, file.doc.preface, newPreface) }
        }
    }

    /** Adds a note at the end of [anchor]'s subtree, as a child or as the next sibling, and opens it. */
    fun createNoteNear(anchor: OrgNote, asChild: Boolean, title: String) {
        viewModelScope.launch {
            attempt { openNote(repository.createNoteNear(anchor, asChild, title)) }
        }
    }

    fun createNote(fileUri: String, title: String) {
        viewModelScope.launch {
            attempt { openNote(repository.createNote(Uri.parse(fileUri), title)) }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            message(e.message ?: e.toString())
            null
        }

    fun message(text: String) {
        _messages.tryEmit(text)
    }
}
