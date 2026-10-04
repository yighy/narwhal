package io.github.yighy.orgline.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yighy.orgline.core.NoteEdit
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.data.NoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Notes : Screen
    data class Note(val fileUri: String, val index: Int, val editing: Boolean = false) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = NoteRepository(app)
    val library = repository.library

    val stack = mutableStateListOf<Screen>(Screen.Notes)

    var query by mutableStateOf("")
    var tag by mutableStateOf<String?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun setFolder(uri: Uri) {
        viewModelScope.launch { repository.setFolder(uri) }
    }

    fun open(note: OrgNote, editing: Boolean = false) {
        stack.add(Screen.Note(note.fileUri, note.index, editing))
    }

    fun openById(id: String) {
        val note = library.value.noteById(id)
        if (note != null) open(note) else message("No note with ID $id")
    }

    fun setEditing(editing: Boolean) {
        val top = stack.last()
        if (top is Screen.Note) stack[stack.lastIndex] = top.copy(editing = editing)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Returns the saved note, or null if it could not be saved. */
    suspend fun save(note: OrgNote, edit: NoteEdit): OrgNote? = attempt { repository.saveNote(note, edit) }

    /** Returns the target's ID (created if needed) and the target as now stored. */
    suspend fun idFor(target: OrgNote): Pair<String, OrgNote>? = attempt { repository.ensureId(target) }

    fun createNote(fileUri: String?, newFileName: String?, title: String) {
        viewModelScope.launch {
            attempt {
                val uri = fileUri?.let(Uri::parse) ?: repository.createFile(newFileName ?: "inbox")
                open(repository.createNote(uri, title), editing = true)
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            message(e.message ?: e.toString())
            null
        }

    private fun message(text: String) {
        _messages.tryEmit(text)
    }
}
