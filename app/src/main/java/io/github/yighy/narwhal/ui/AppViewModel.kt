package io.github.yighy.narwhal.ui

import io.github.yighy.narwhal.data.NoteSpacing
import io.github.yighy.narwhal.data.ThemeMode
import java.time.LocalDateTime
import io.github.yighy.narwhal.core.SearchQuery
import android.content.ActivityNotFoundException
import android.content.Intent
import io.github.yighy.narwhal.data.SavedSearch
import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yighy.narwhal.core.IdLinks
import io.github.yighy.narwhal.core.Keywords
import io.github.yighy.narwhal.core.NoteEdit
import io.github.yighy.narwhal.core.Outline
import io.github.yighy.narwhal.core.Placement
import io.github.yighy.narwhal.core.SectionEditor
import io.github.yighy.narwhal.core.Subtree
import io.github.yighy.narwhal.core.NoteStates
import io.github.yighy.narwhal.core.NoteTree
import io.github.yighy.narwhal.core.OrgTime
import io.github.yighy.narwhal.core.OrgNote
import io.github.yighy.narwhal.NarwhalApplication
import io.github.yighy.narwhal.data.LinkRenaming
import io.github.yighy.narwhal.data.NoteRepository
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Screen {
    data object Notebooks : Screen
    data class Notebook(val fileUri: String) : Screen
    data class Note(val fileUri: String, val index: Int) : Screen
    data object Settings : Screen
    data object Agenda : Screen
    data object Searches : Screen

    /**
     * A note being created: nothing is written to the file until it gets a title.
     * [anchor] and [placement] say where it goes; without them it is added at the end of the notebook.
     */
    data class NewNote(val fileUri: String, val anchor: OrgNote? = null, val placement: Placement? = null) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val narwhal = app as NarwhalApplication
    private val repository = narwhal.repository
    val library = repository.library

    private val appSettings = narwhal.settings
    val settings = appSettings.values

    val canScheduleExactAlarms: Boolean get() = narwhal.scheduler.canScheduleExact()

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

    /** Opens the search (of the current screen) with [query], e.g. "it.todo". */
    fun searchFor(query: String) {
        this.query = query
        tag = null
        searchActive = true
    }

    // ---- Saved searches ----

    val savedSearches = appSettings.savedSearches

    /** Saved search being named or edited (index null: a new one), shown in a dialog. */
    data class SearchDraft(val index: Int?, val name: String, val query: String)

    var searchDraft by mutableStateOf<SearchDraft?>(null)
        private set

    /**
     * Offers to save the search being made (with its tag chip); in a notebook, the search keeps
     * to it with a b. condition.
     */
    fun saveCurrentSearch(notebookName: String?) {
        var text = listOfNotNull(query.trim().takeIf { it.isNotEmpty() }, tag?.let { "t.\"$it\"" }).joinToString(" ")
        if (notebookName != null && !Regex("""(^|\s)\.?b\.""").containsMatchIn(text)) text = "b.\"$notebookName\" $text"
        if (text.isNotBlank()) searchDraft = SearchDraft(null, query.trim().ifEmpty { text }, text)
    }

    fun editSavedSearch(index: Int) {
        val search = savedSearches.value.getOrNull(index) ?: return
        searchDraft = SearchDraft(index, search.name, search.query)
    }

    fun confirmSearchDraft(name: String, query: String) {
        val draft = searchDraft ?: return
        searchDraft = null
        val search = SavedSearch(name.trim(), query.trim())
        val list = savedSearches.value
        appSettings.setSavedSearches(if (draft.index == null) list + search else list.mapIndexed { i, s -> if (i == draft.index) search else s })
        if (draft.index == null) message("Search saved")
    }

    fun deleteSearchDraft() {
        val index = searchDraft?.index
        searchDraft = null
        if (index != null) appSettings.setSavedSearches(savedSearches.value.filterIndexed { i, _ -> i != index })
    }

    fun dismissSearchDraft() {
        searchDraft = null
    }

    fun deleteSavedSearches(indices: Set<Int>) {
        appSettings.setSavedSearches(savedSearches.value.filterIndexed { i, _ -> i !in indices })
        message(if (indices.size == 1) "Search deleted" else "${indices.size} searches deleted")
    }

    /** Moves a saved search by [delta] places; returns its new index. */
    fun moveSavedSearch(index: Int, delta: Int): Int {
        val list = savedSearches.value.toMutableList()
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return index
        list.add(target, list.removeAt(index))
        appSettings.setSavedSearches(list)
        return target
    }

    /** Runs a saved search over all notebooks. */
    fun runSavedSearch(search: SavedSearch) {
        openHome()
        searchFor(search.query)
    }

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

    private var checking = false

    /** Reloads files changed by other apps (Syncthing, editors…), without showing it. */
    fun checkForChanges() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            runCatching { repository.refresh(quiet = true) }
            checking = false
        }
    }

    fun pullToRefresh() {
        refreshing = true
        viewModelScope.launch {
            repository.refresh()
            refreshing = false
        }
    }

    fun setShowContentInNotebook(show: Boolean) = appSettings.setShowContentInNotebook(show)

    fun setNoteSpacing(spacing: NoteSpacing) = appSettings.setNoteSpacing(spacing)

    fun setShowIdProperty(show: Boolean) = appSettings.setShowIdProperty(show)
    fun setRemindScheduled(on: Boolean) = appSettings.setRemindScheduled(on)
    fun setRemindDeadline(on: Boolean) = appSettings.setRemindDeadline(on)
    fun setRemindAllDay(on: Boolean) = appSettings.setRemindAllDay(on)
    fun setAllDayTime(time: LocalTime) = appSettings.setAllDayTime(time)
    fun setAddIdToNewNotes(on: Boolean) = appSettings.setAddIdToNewNotes(on)

    fun setAddCreatedToNewNotes(on: Boolean) = appSettings.setAddCreatedToNewNotes(on)

    fun setUseAlarmClock(on: Boolean) = appSettings.setUseAlarmClock(on)

    fun setSnoozeMinutes(minutes: Int) = appSettings.setSnoozeMinutes(minutes)

    fun setLinkRenaming(mode: LinkRenaming) = appSettings.setLinkRenaming(mode)

    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)

    fun setDynamicColor(on: Boolean) = appSettings.setDynamicColor(on)

    val notebookIcons = appSettings.notebookIcons

    fun setNotebookIcon(fileUri: String, emoji: String?) = appSettings.setNotebookIcon(fileUri, emoji)

    /** Sets the default state keywords from "TODO NEXT | DONE"; false if there is no keyword. */
    fun setKeywords(spec: String): Boolean {
        val keywords = Keywords.parse(spec) ?: return false
        appSettings.setKeywords(keywords)
        return true
    }

    fun openSettings() = push(Screen.Settings)

    fun openSearches() {
        if (stack.last() != Screen.Searches) push(Screen.Searches)
    }

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

    /**
     * Opens a link's target: id: a note, file: a notebook (.org) or a file of the folder, anything
     * else (https:, mailto:, tel:, sms:, geo:…) in the app Android picks.
     */
    fun openLink(target: String) {
        val link = target.trim()
        when {
            link.startsWith("id:", ignoreCase = true) -> openById(link.substring(3).trim())
            link.startsWith("file:", ignoreCase = true) -> openFileLink(link.substring(5))
            else -> view(Uri.parse(link))
        }
    }

    /** file:gtd.org opens that notebook; file:Download/document.txt a file of the folder. */
    private fun openFileLink(path: String) {
        // Org's search options (file:gtd.org::*Heading) are not followed: the file opens
        val relative = path.substringBefore("::").trim().removePrefix("./").removePrefix("/")
        val files = library.value.files
        val notebook = files.firstOrNull { it.ref.path.equals(relative, ignoreCase = true) }
            ?: files.firstOrNull { it.ref.name.equals(relative, ignoreCase = true) }
        if (notebook != null) {
            openNotebook(notebook.uri)
            return
        }
        viewModelScope.launch {
            val (uri, type) = attempt { repository.findInFolder(relative) } ?: run {
                message("No file $relative in the folder")
                return@launch
            }
            view(uri, type)
        }
    }

    private fun view(uri: Uri, type: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            message("No app can open $uri")
        }
    }

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

    /** Saves are queued, each from the note as last written, so quick edits never undo each other. */
    private val saveLock = Mutex()

    /**
     * Saves [change] applied to the note as it is now (not as it was when the edit started).
     * A note being created (not yet in its file) only updates the draft.
     */
    private suspend fun saveLatest(note: OrgNote, change: (OrgNote) -> NoteEdit): OrgNote? {
        if (note.index == DRAFT_INDEX) {
            val screen = newNoteScreen ?: return null
            return saveDraft(change(draftNote(screen)))
        }
        return saveLock.withLock {
            val latest = library.value.note(note.fileUri, note.index) ?: note
            attempt { repository.saveNote(latest, change(latest)) }
        }
    }

    /** Returns the saved note, or null if it could not be saved. */
    suspend fun save(note: OrgNote, edit: NoteEdit): OrgNote? = saveLatest(note) { edit }

    fun update(note: OrgNote, change: (NoteEdit) -> NoteEdit) {
        viewModelScope.launch { saveLatest(note) { change(it.toEdit()) } }
    }

    /** Links to a renamed note that still show its old title, until the user says to update them. */
    data class LinkRename(val id: String, val oldTitle: String, val newTitle: String, val notes: Int)

    var linkRename by mutableStateOf<LinkRename?>(null)
        private set

    /**
     * Saves a new title, then, as set in the settings, updates the links to the note that still
     * show the old one, asks first, or leaves them.
     */
    fun rename(note: OrgNote, title: String) {
        val oldTitle = note.title
        viewModelScope.launch {
            val saved = saveLatest(note) { it.toEdit().copy(title = title) } ?: return@launch
            val mode = settings.value.linkRenaming
            if (mode == LinkRenaming.Never) return@launch
            val id = saved.id ?: return@launch
            val notes = library.value.notes.count { IdLinks.renameLinks(it.raw, id, oldTitle, title) != it.raw }
            if (notes == 0) return@launch
            val rename = LinkRename(id, oldTitle, title, notes)
            if (mode == LinkRenaming.Always) renameLinks(rename) else linkRename = rename
        }
    }

    fun updateRenamedLinks() {
        val rename = linkRename ?: return
        linkRename = null
        viewModelScope.launch { renameLinks(rename) }
    }

    private suspend fun renameLinks(rename: LinkRename) {
        val changed = attempt { repository.renameLinks(rename.id, rename.oldTitle, rename.newTitle) } ?: return
        message(if (changed == 1) "Link updated in 1 note" else "Links updated in $changed notes")
    }

    fun keepRenamedLinks() {
        linkRename = null
    }

    /** Changes the state with Org semantics (CLOSED time, repeating tasks moving forward). */
    fun setState(note: OrgNote, target: String?) {
        val keywords = library.value.file(note.fileUri)?.doc?.keywords ?: repository.defaultKeywords
        viewModelScope.launch {
            var shifted = false
            val saved = saveLatest(note) { latest ->
                NoteStates.apply(latest.toEdit(), target, keywords).also { shifted = it.shifted }.edit
            } ?: return@launch
            if (shifted) {
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

    /** Adds a note above, under or below [anchor] and opens it. */
    // ---- New note: shown in the note screen, written to the file once it has a title ----

    /** Fields of the note being created, kept in memory until it has a title. */
    var newNoteDraft by mutableStateOf(EMPTY_DRAFT)
        private set

    /** Opens the note screen for a new note, title being edited; see [saveDraft]. */
    fun startNewNote(fileUri: String, anchor: OrgNote? = null, placement: Placement? = null) {
        openSwipeRow = null
        newNoteDraft = EMPTY_DRAFT
        val screen = Screen.NewNote(fileUri, anchor, placement)
        newNoteScreen = screen
        push(screen)
    }

    /** The draft as a note for the note screen; [DRAFT_INDEX] marks it as not in the file yet. */
    fun draftNote(screen: Screen.NewNote): OrgNote {
        val d = newNoteDraft
        val keywords = library.value.file(screen.fileUri)?.doc?.keywords ?: Keywords.DEFAULT
        val level = (screen.anchor?.level ?: 1) + if (screen.placement == Placement.Under) 1 else 0
        return OrgNote(
            fileUri = screen.fileUri,
            fileName = library.value.file(screen.fileUri)?.ref?.name ?: "",
            index = DRAFT_INDEX,
            level = level,
            state = d.state,
            isDone = d.state != null && d.state in keywords.done,
            priority = d.priority,
            title = d.title,
            tags = d.tags,
            scheduled = d.scheduled,
            deadline = d.deadline,
            closed = d.closed,
            properties = d.properties,
            content = d.content,
            raw = "",
        )
    }

    /**
     * Updates the draft; once it has a title, writes the note with all its fields and
     * replaces the new-note screen with the note's screen.
     */
    /** The note being created, even after its screen was left (back saves it). */
    private var newNoteScreen: Screen.NewNote? = null

    private suspend fun saveDraft(edit: NoteEdit): OrgNote? {
        val screen = newNoteScreen ?: return null
        newNoteDraft = edit
        if (edit.title.isBlank()) {
            return draftNote(screen)
        }
        // With the settings on, the new note gets an ID and a CREATED property right away
        val added = buildList {
            if (settings.value.addIdToNewNotes && edit.properties.none { it.first.equals("ID", true) }) {
                add("ID" to UUID.randomUUID().toString().uppercase())
            }
            if (settings.value.addCreatedToNewNotes && edit.properties.none { it.first.equals(SearchQuery.CREATED_PROPERTY, true) }) {
                add(SearchQuery.CREATED_PROPERTY to OrgTime.inactive(LocalDateTime.now()))
            }
        }
        val full = if (added.isEmpty()) edit else edit.copy(properties = edit.properties + added)
        val note = attempt {
            val created = if (screen.anchor != null && screen.placement != null) {
                repository.createNoteNear(screen.anchor, screen.placement, edit.title)
            } else {
                repository.createNote(Uri.parse(screen.fileUri), edit.title)
            }
            // Fields set before the title (tags, state, dates, content…) and the ID
            if (full != created.toEdit().copy(title = full.title)) repository.saveNote(created, full) else created
        } ?: return null
        newNoteScreen = null
        // If the screen is still shown, it becomes the note's screen
        val i = stack.indexOf(screen)
        if (i >= 0) {
            navigatedForward = true
            stack[i] = Screen.Note(note.fileUri, note.index)
        }
        return note
    }

    // ---- Multiple selection of notebooks on the home screen ----

    var selectedNotebooks by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleNotebookSelected(fileUri: String) {
        selectedNotebooks = if (fileUri in selectedNotebooks) selectedNotebooks - fileUri else selectedNotebooks + fileUri
    }

    fun clearNotebookSelection() {
        selectedNotebooks = emptySet()
    }

    /** Deletes the selected notebooks' files. */
    /** Renames the selected notebook (its file, and its #+TITLE if it has one). */
    fun renameSelectedNotebook(title: String) {
        val uri = selectedNotebooks.singleOrNull() ?: return
        selectedNotebooks = emptySet()
        viewModelScope.launch {
            val newUri = attempt { repository.renameFile(uri, title) } ?: return@launch
            // The icon follows the file
            notebookIcons.value[uri]?.takeIf { newUri != uri }?.let {
                appSettings.setNotebookIcon(newUri, it)
                appSettings.setNotebookIcon(uri, null)
            }
        }
    }

    fun deleteSelectedNotebooks() {
        val uris = selectedNotebooks
        selectedNotebooks = emptySet()
        uris.forEach { appSettings.setNotebookIcon(it, null) }
        viewModelScope.launch {
            attempt {
                repository.deleteFiles(uris)
                message(if (uris.size == 1) "Notebook deleted" else "${uris.size} notebooks deleted")
            }
        }
    }

    // ---- Multiple selection in a notebook (long press) ----

    data class Selection(val fileUri: String, val indices: Set<Int>)

    var selection by mutableStateOf<Selection?>(null)
        private set

    /** Notes cut or copied, with their sub-notes; can be pasted in any notebook. */
    var clipboard by mutableStateOf<List<Subtree>>(emptyList())
        private set

    fun toggleSelected(note: OrgNote) {
        val current = selection?.takeIf { it.fileUri == note.fileUri }
        val indices = current?.indices.orEmpty().let { if (note.index in it) it - note.index else it + note.index }
        selection = if (indices.isEmpty()) null else Selection(note.fileUri, indices)
    }

    fun clearSelection() {
        selection = null
    }

    fun selectAll(notes: List<OrgNote>) {
        val uri = notes.firstOrNull()?.fileUri ?: return
        selection = Selection(uri, notes.map { it.index }.toSet())
    }

    fun selectedNotes(): List<OrgNote> {
        val s = selection ?: return emptyList()
        return s.indices.sorted().mapNotNull { library.value.note(s.fileUri, it) }
    }

    /** Runs [block] on the selected notes; [keep] returns the selection to keep afterwards (null clears it). */
    private fun withSelection(keep: (List<OrgNote>) -> Set<Int>? = { null }, block: suspend (List<OrgNote>) -> Unit) {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        viewModelScope.launch {
            attempt { block(notes) }
            selection = keep(notes)?.takeIf { it.isNotEmpty() }?.let { Selection(notes.first().fileUri, it) }
        }
    }

    private fun count(n: Int) = if (n == 1) "1 note" else "$n notes"

    fun copySelection() = withSelection { notes ->
        clipboard = repository.copySubtrees(notes.first().fileUri, notes)
        message("${count(clipboard.size)} copied")
    }

    fun cutSelection() = withSelection { notes ->
        var cut = emptyList<Subtree>()
        repository.modifyFile(notes.first().fileUri, notes) { doc, indices ->
            cut = Outline.extract(doc, indices)
            Outline.delete(doc, indices)
        }
        clipboard = cut
        message("${count(cut.size)} cut")
    }

    fun pasteAtSelection(placement: Placement) = withSelection { notes ->
        val subtrees = clipboard
        repository.modifyFile(notes.first().fileUri, notes.take(1)) { doc, (index) ->
            val (at, level) = Outline.target(doc, index, placement)
            Outline.insert(doc, at, level, subtrees)
        }
    }

    fun deleteSelection() = withSelection { notes ->
        var deleted = 0
        repository.modifyFile(notes.first().fileUri, notes) { doc, indices ->
            deleted = Outline.roots(doc, indices).size
            Outline.delete(doc, indices)
        }
        message("${count(deleted)} deleted")
    }

    enum class Move { Up, Down, Promote, Demote }

    fun moveSelection(move: Move) {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        var newIndex: Int? = null
        withSelection(keep = { list -> newIndex?.let { setOf(it) } ?: list.map { it.index }.toSet() }) {
            repository.modifyFile(notes.first().fileUri, notes) { doc, indices ->
                when (move) {
                    Move.Up -> Outline.moveUp(doc, indices.single())?.also { newIndex = it.second }?.first ?: doc
                    Move.Down -> Outline.moveDown(doc, indices.single())?.also { newIndex = it.second }?.first ?: doc
                    Move.Promote -> Outline.shift(doc, indices, -1)
                    Move.Demote -> Outline.shift(doc, indices, 1)
                }
            }
        }
    }

    fun moveSelectionTo(fileUri: String) = withSelection { notes ->
        repository.moveToFile(notes, fileUri)
        message("${count(notes.size)} moved to ${library.value.file(fileUri)?.title}")
    }

    /** Applies [change] to every selected note in one write, keeping the selection. */
    private fun editSelection(change: (OrgNote, Keywords) -> NoteEdit) =
        withSelection(keep = { notes -> notes.map { it.index }.toSet() }) { notes ->
            val uri = notes.first().fileUri
            repository.modifyFile(uri, notes) { doc, indices ->
                Outline.replaceSections(doc, indices.withIndex().associate { (k, i) ->
                    val section = doc.sections[i]
                    i to SectionEditor.rewrite(section.raw, section.level, doc.keywords, change(notes[k], doc.keywords))
                })
            }
        }

    fun setStateOfSelection(target: String?) = editSelection { note, keywords ->
        NoteStates.apply(note.toEdit(), target, keywords).edit
    }

    /** Marks the selection done, or back to to-do when all selected notes are done. */
    fun toggleDoneOfSelection() {
        val notes = selectedNotes()
        val allDone = notes.isNotEmpty() && notes.all { it.isDone }
        editSelection { note, keywords ->
            val target = if (allDone) keywords.todo.firstOrNull() else keywords.done.firstOrNull()
            NoteStates.apply(note.toEdit(), target, keywords).edit
        }
    }

    fun setScheduledOfSelection(timestamp: String?) = editSelection { note, _ -> note.toEdit().copy(scheduled = timestamp) }

    fun setDeadlineOfSelection(timestamp: String?) = editSelection { note, _ -> note.toEdit().copy(deadline = timestamp) }

    fun startNewNoteNearSelection(placement: Placement) {
        val note = selectedNotes().singleOrNull() ?: return
        selection = null
        startNewNote(note.fileUri, note, placement)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            message(e.message ?: e.toString())
            null
        }

    companion object {
        /** Index of a note that is not in its file yet (being created). */
        const val DRAFT_INDEX = -1
        private val EMPTY_DRAFT = NoteEdit(title = "", state = null, tags = emptyList(), properties = emptyList(), content = "")
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }
}
