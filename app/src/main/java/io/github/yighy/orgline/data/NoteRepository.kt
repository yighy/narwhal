package io.github.yighy.orgline.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.yighy.orgline.core.NoteEdit
import io.github.yighy.orgline.core.OrgDocument
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.core.SectionEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Thrown when a note was changed by another app since it was loaded. */
class NoteChangedException : Exception("The note was changed by another app. It has been reloaded.")

/**
 * Keeps an in-memory index of all notes in the chosen folder.
 *
 * The .org files are the only storage: every change is written to the file right away,
 * and files modified by other apps are re-read on [refresh].
 */
class NoteRepository(context: Context) {

    data class LoadedFile(val ref: OrgFolder.FileRef, val doc: OrgDocument, val notes: List<OrgNote>)

    data class Library(
        val folderUri: Uri? = null,
        val files: List<LoadedFile> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    ) {
        val notes: List<OrgNote> by lazy { files.flatMap { it.notes } }
        val tags: List<String> by lazy { notes.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() } }

        fun noteById(id: String): OrgNote? = notes.firstOrNull { it.id == id }
        fun note(fileUri: String, index: Int): OrgNote? =
            files.firstOrNull { it.ref.uri.toString() == fileUri }?.notes?.getOrNull(index)
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences("orgline", Context.MODE_PRIVATE)
    private val writeLock = Mutex()

    private val _library = MutableStateFlow(Library(folderUri = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)))
    val library: StateFlow<Library> = _library

    private fun folder(): OrgFolder? = _library.value.folderUri?.let { OrgFolder(resolver, it) }

    suspend fun setFolder(uri: Uri) {
        resolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        _library.value = Library(folderUri = uri)
        refresh()
    }

    /** Re-reads files that are new or were modified since the last load. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val folder = folder() ?: return@withContext
        _library.update { it.copy(loading = true, error = null) }
        try {
            val cached = _library.value.files.associateBy { it.ref.uri }
            val files = folder.listOrgFiles().map { ref ->
                cached[ref.uri]?.takeIf { it.ref.lastModified == ref.lastModified } ?: load(folder, ref)
            }
            _library.update { it.copy(files = files, loading = false) }
        } catch (e: Exception) {
            _library.update { it.copy(loading = false, error = e.message ?: e.toString()) }
        }
    }

    private fun load(folder: OrgFolder, ref: OrgFolder.FileRef): LoadedFile {
        val doc = OrgDocument.parse(folder.read(ref.uri))
        return LoadedFile(ref, doc, OrgNote.fromDocument(doc, ref.uri.toString(), ref.name))
    }

    suspend fun saveNote(note: OrgNote, edit: NoteEdit): OrgNote =
        modifySection(note) { doc, section -> SectionEditor.rewrite(section.raw, section.level, doc.keywords, edit) }

    /** Returns the note's ID, adding a new one to its file if it has none. */
    suspend fun ensureId(note: OrgNote): Pair<String, OrgNote> {
        note.id?.let { return it to note }
        val id = UUID.randomUUID().toString().uppercase()
        val updated = modifySection(note) { _, section -> SectionEditor.addProperty(section.raw, "ID", id) }
        return id to updated
    }

    suspend fun createNote(fileUri: Uri, title: String): OrgNote = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val doc = OrgDocument.parse(folder.read(fileUri)).appendNote(title)
            folder.write(fileUri, doc.toFileText())
            reloadFile(folder, fileUri).notes.last()
        }
    }

    suspend fun createFile(name: String): Uri = withContext(Dispatchers.IO) {
        val folder = folder() ?: error("No folder")
        folder.createFile(name).also { refresh() }
    }

    /**
     * Re-reads the file, finds the note's section by its exact text and replaces it.
     * Fails if the section no longer exists as loaded, so changes from other apps are never overwritten.
     */
    private suspend fun modifySection(
        note: OrgNote,
        transform: (OrgDocument, OrgDocument.Section) -> String,
    ): OrgNote = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(note.fileUri)
            val doc = OrgDocument.parse(folder.read(uri))

            val index = doc.sections.getOrNull(note.index)?.takeIf { it.raw == note.raw }?.let { note.index }
                ?: doc.sections.indexOfFirst { it.raw == note.raw }.takeIf { it >= 0 && doc.sections.count { s -> s.raw == note.raw } == 1 }

            if (index == null) {
                reloadFile(folder, uri)
                throw NoteChangedException()
            }

            val section = doc.sections[index]
            val newRaw = transform(doc, section)
            if (newRaw != section.raw) {
                folder.write(uri, doc.replaceSection(section, newRaw).toFileText())
            }
            reloadFile(folder, uri).notes[index]
        }
    }

    private fun reloadFile(folder: OrgFolder, uri: Uri): LoadedFile {
        val current = _library.value.files.first { it.ref.uri == uri }
        val loaded = load(folder, current.ref.copy(lastModified = folder.lastModified(uri)))
        _library.update { lib -> lib.copy(files = lib.files.map { if (it.ref.uri == uri) loaded else it }) }
        return loaded
    }

    companion object {
        private const val KEY_FOLDER = "folder_uri"
    }
}
