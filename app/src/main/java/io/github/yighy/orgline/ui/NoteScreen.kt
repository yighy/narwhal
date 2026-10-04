package io.github.yighy.orgline.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.IdLinks
import io.github.yighy.orgline.core.Keywords
import io.github.yighy.orgline.core.NoteTree
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.core.OrgTime
import io.github.yighy.orgline.core.Placement
import io.github.yighy.orgline.data.NoteRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import io.github.yighy.orgline.core.NoteEdit

/** Which part of the note is being edited. */
private enum class Editing { None, Title, Content }

/** Metadata dialogs. */
private sealed interface MetaDialog {
    data object Tags : MetaDialog
    data object State : MetaDialog
    data object Priority : MetaDialog
    data object Scheduled : MetaDialog
    data object Deadline : MetaDialog
    data class Property(val name: String, val value: String, val isNew: Boolean) : MetaDialog
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    note: OrgNote,
    snackbar: SnackbarHostState,
    /** Set while the note is being created: it is written to the file once its title is saved. */
    newNote: Screen.NewNote? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = library.file(note.fileUri)
    val keywords = file?.doc?.keywords ?: Keywords.DEFAULT

    // A new note starts with its title being edited
    var editing by remember { mutableStateOf(if (newNote != null) Editing.Title else Editing.None) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var dialog by remember { mutableStateOf<MetaDialog?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    // The note as now loaded, also when saving as the screen goes away
    val currentNote by rememberUpdatedState(note)

    fun withText(edit: NoteEdit, what: Editing, text: String) =
        if (what == Editing.Title) edit.copy(title = text) else edit.copy(content = text)

    /** Saves the title or content being edited, if it changed, and leaves edit mode (no save button). */
    fun commit() {
        val what = editing
        if (what == Editing.None) return
        editing = Editing.None
        val text = draft.text
        val before = if (what == Editing.Title) currentNote.title else currentNote.content
        if (text != before) viewModel.update(currentNote) { withText(it, what, text) }
    }

    fun startEditing(what: Editing) {
        commit()
        val text = if (what == Editing.Title) note.title else note.content
        draft = TextFieldValue(text, TextRange(text.length))
        editing = what
    }

    fun openDialog(d: MetaDialog) {
        commit()
        dialog = d
    }

    BackHandler(enabled = editing != Editing.None) { commit() }
    DisposableEffect(Unit) { onDispose { commit() } }

    // "id:" completion while editing the title or the content
    val query = draft.takeIf { editing != Editing.None && it.selection.collapsed }
        ?.let { IdLinks.queryAt(it.text, it.selection.start) }
    val suggestions = remember(query?.text, library) {
        query?.let { IdLinks.suggestions(library.notes, it.text) }.orEmpty()
    }

    fun insertLink(target: OrgNote) {
        val q = query ?: return
        val what = editing
        val text = draft.text
        scope.launch {
            val (id, _) = viewModel.idFor(target) ?: return@launch
            val link = IdLinks.format(id, displayTitle(target.title))
            val linked = text.replaceRange(q.start, q.end, link)
            if (editing == what) {
                draft = TextFieldValue(linked, TextRange(q.start + link.length))
            } else {
                // The field was saved meanwhile (focus moved): save the link too
                viewModel.update(currentNote) { withText(it, what, linked) }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .sharedContainer("note-${note.fileUri}#${note.index}", RectangleShape)
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {},
                colors = blendedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Nothing to link to or add next to until the note exists
                    if (newNote == null) Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Copy link to this note") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    moreMenu = false
                                    scope.launch {
                                        val (id, _) = viewModel.idFor(note) ?: return@launch
                                        context.getSystemService(ClipboardManager::class.java)
                                            .setPrimaryClip(ClipData.newPlainText("Org link", IdLinks.format(id, displayTitle(note.title))))
                                        viewModel.message("Link copied")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New note above") },
                                leadingIcon = { Icon(OrglineIcons.NewAbove, null) },
                                onClick = { moreMenu = false; viewModel.startNewNote(note.fileUri, note, Placement.Above) },
                            )
                            DropdownMenuItem(
                                text = { Text("New sub-note") },
                                leadingIcon = { Icon(OrglineIcons.SubNote, null) },
                                onClick = { moreMenu = false; viewModel.startNewNote(note.fileUri, note, Placement.Under) },
                            )
                            DropdownMenuItem(
                                text = { Text("New note below") },
                                leadingIcon = { Icon(OrglineIcons.NewBelow, null) },
                                onClick = { moreMenu = false; viewModel.startNewNote(note.fileUri, note, Placement.Below) },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (suggestions.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(suggestions, key = { it.fileUri + "#" + it.index }) { target ->
                            ListItem(
                                headlineContent = { Text(displayTitle(target.title)) },
                                supportingContent = { Text(library.file(target.fileUri)?.title ?: target.fileName) },
                                modifier = Modifier.clickable { insertLink(target) },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Breadcrumbs: notebook · ancestors (or where a new note goes)
            FlowRow(verticalArrangement = Arrangement.Center) {
                val crumbs = buildList<Pair<String, () -> Unit>> {
                    add((file?.title ?: note.fileName) to { viewModel.openNotebook(note.fileUri) })
                    if (newNote != null) add(positionLabel(newNote) to {})
                    else file?.notes?.let { notes ->
                        NoteTree.ancestors(notes, note.index).forEach { a -> add(displayTitle(a.title) to { viewModel.openNote(a) }) }
                    }
                }
                crumbs.forEachIndexed { i, (label, open) ->
                    if (i > 0) Text("  ·  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = open),
                    )
                }
            }

            // Title
            if (editing == Editing.Title) {
                DraftField(
                    value = draft,
                    onValueChange = {
                        if (editing == Editing.Title) draft = TextFieldValue(it.text.replace("\n", " "), it.selection, it.composition)
                    },
                    label = "Title",
                    singleLine = false,
                    onFocusLost = { if (editing == Editing.Title) commit() },
                    onDone = { if (editing == Editing.Title) commit() },
                )
            } else if (note.title.isBlank()) {
                Text(
                    if (newNote != null) "Add a title to create the note" else "Untitled",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { startEditing(Editing.Title) }
                        .padding(vertical = 8.dp),
                )
            } else {
                // Links in the title open their note; elsewhere, a tap edits the title
                LinkedText(
                    note.title,
                    MaterialTheme.typography.headlineMedium,
                    viewModel::openById,
                    Modifier
                        .fillMaxWidth()
                        .clickable { startEditing(Editing.Title) }
                        .padding(vertical = 8.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Metadata: only what is set, tap to change
            if (note.tags.isNotEmpty()) {
                MetaRow("Tags", onClick = { openDialog(MetaDialog.Tags) }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        note.tags.forEach { SuggestionChip(onClick = { openDialog(MetaDialog.Tags) }, label = { Text(it) }) }
                    }
                }
            }
            note.state?.let { MetaRow("State", onClick = { openDialog(MetaDialog.State) }) { StateLabel(it, note.isDone) } }
            note.priority?.let { MetaRow("Priority", it, onClick = { openDialog(MetaDialog.Priority) }) }
            note.scheduled?.let { MetaRow("Scheduled", OrgTime.display(it).orEmpty(), onClick = { openDialog(MetaDialog.Scheduled) }) }
            note.deadline?.let { MetaRow("Deadline", OrgTime.display(it).orEmpty(), onClick = { openDialog(MetaDialog.Deadline) }) }
            note.closed?.let { MetaRow("Closed", OrgTime.display(it).orEmpty(), onClick = null) }
            note.properties.forEach { (name, value) ->
                MetaRow(name, value, onClick = { openDialog(MetaDialog.Property(name, value, isNew = false)) })
            }

            Box {
                TextButton(onClick = { commit(); addMenu = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    listOfNotNull(
                        ("Tags" to MetaDialog.Tags).takeIf { note.tags.isEmpty() },
                        ("State" to MetaDialog.State).takeIf { note.state == null },
                        ("Priority" to MetaDialog.Priority).takeIf { note.priority == null },
                        ("Scheduled time" to MetaDialog.Scheduled).takeIf { note.scheduled == null },
                        ("Deadline time" to MetaDialog.Deadline).takeIf { note.deadline == null },
                        "Property" to MetaDialog.Property("", "", isNew = true),
                    ).forEach { (label, open) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { addMenu = false; dialog = open })
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Content
            if (editing == Editing.Content) {
                DraftField(
                    value = draft,
                    onValueChange = { if (editing == Editing.Content) draft = it },
                    label = "Content",
                    singleLine = false,
                    minLines = 8,
                    onFocusLost = { if (editing == Editing.Content) commit() },
                )
                Text(
                    "Type id: to link to another note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else if (note.content.isBlank()) {
                Text(
                    "Add content",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { startEditing(Editing.Content) }
                        .padding(vertical = 12.dp),
                )
            } else {
                LinkedText(
                    note.content,
                    MaterialTheme.typography.bodyLarge,
                    viewModel::openById,
                    Modifier
                        .fillMaxWidth()
                        .clickable { startEditing(Editing.Content) }
                        .padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.heightIn(min = 96.dp))
        }
    }

    when (val d = dialog) {
        null -> Unit
        MetaDialog.Tags -> TagsDialog(
            initial = note.tags,
            allTags = library.tags,
            onDismiss = { dialog = null },
            onConfirm = { tags -> dialog = null; viewModel.update(note) { it.copy(tags = tags) } },
        )
        MetaDialog.State -> ChoiceDialog(
            title = "State",
            options = listOf(null) + keywords.all,
            selected = note.state,
            onDismiss = { dialog = null },
            onSelect = { s -> dialog = null; viewModel.setState(note, s) },
        )
        MetaDialog.Priority -> ChoiceDialog(
            title = "Priority",
            options = listOf(null, "A", "B", "C"),
            selected = note.priority,
            onDismiss = { dialog = null },
            onSelect = { p -> dialog = null; viewModel.update(note) { it.copy(priority = p) } },
        )
        MetaDialog.Scheduled -> TimestampDialog(
            initial = note.scheduled,
            onDismiss = { dialog = null },
            onConfirm = { t -> dialog = null; viewModel.update(note) { it.copy(scheduled = t) } },
        )
        MetaDialog.Deadline -> TimestampDialog(
            initial = note.deadline,
            onDismiss = { dialog = null },
            onConfirm = { t -> dialog = null; viewModel.update(note) { it.copy(deadline = t) } },
        )
        is MetaDialog.Property -> PropertyDialog(
            name = d.name,
            value = d.value,
            isNew = d.isNew,
            onDismiss = { dialog = null },
            onConfirm = { name, value ->
                dialog = null
                viewModel.update(note) { edit ->
                    edit.copy(
                        properties = if (d.isNew) {
                            edit.properties + (name to value)
                        } else {
                            edit.properties.map { if (it.first.equals(d.name, ignoreCase = true)) name to value else it }
                        },
                    )
                }
            },
            onRemove = {
                dialog = null
                viewModel.update(note) { edit ->
                    edit.copy(properties = edit.properties.filterNot { it.first.equals(d.name, ignoreCase = true) })
                }
            },
        )
    }


}

/** "Label    value" line; [onClick] null makes it read-only. */
@Composable
private fun MetaRow(label: String, value: String, onClick: (() -> Unit)?) {
    MetaRow(label, onClick) { Text(value, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun MetaRow(label: String, onClick: (() -> Unit)?, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(130.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
    }
}

fun newNoteTitle(placement: Placement) = when (placement) {
    Placement.Above -> "New note above"
    Placement.Under -> "New sub-note"
    Placement.Below -> "New note below"
}

/** Where a new note goes, e.g. "sub-note of Design". */
private fun positionLabel(screen: Screen.NewNote): String {
    val anchor = screen.anchor?.let { displayTitle(it.title) } ?: return "new note at the end"
    return when (screen.placement) {
        Placement.Above -> "new note above $anchor"
        Placement.Under -> "sub-note of $anchor"
        Placement.Below, null -> "new note below $anchor"
    }
}
