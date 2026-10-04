package io.github.yighy.orgline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.IdLinks
import io.github.yighy.orgline.core.Keywords
import io.github.yighy.orgline.core.NoteEdit
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.data.NoteRepository
import kotlinx.coroutines.launch

/** Title with id: links shown as their description. */
fun displayTitle(title: String): String {
    val links = IdLinks.find(title)
    if (links.isEmpty()) return title
    return buildString {
        var last = 0
        links.forEach { link ->
            append(title, last, link.range.first)
            append(link.description ?: link.id)
            last = link.range.last + 1
        }
        append(title, last, title.length)
    }
}

@Composable
fun NoteScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    note: OrgNote,
    editing: Boolean,
    snackbar: SnackbarHostState,
) {
    if (editing) {
        val keywords = library.files.firstOrNull { it.ref.uri.toString() == note.fileUri }?.doc?.keywords ?: Keywords.DEFAULT
        NoteEditor(viewModel, library, note, keywords, snackbar)
    } else {
        NoteView(viewModel, note, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteView(viewModel: AppViewModel, note: OrgNote, snackbar: SnackbarHostState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note.fileName, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.setEditing(true) }) { Icon(Icons.Default.Edit, "Edit") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (note.state != null || note.priority != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    note.state?.let {
                        Text(
                            it,
                            fontWeight = FontWeight.Bold,
                            color = if (note.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    note.priority?.let { Text("Priority $it", color = MaterialTheme.colorScheme.secondary) }
                }
            }

            LinkedText(note.title, MaterialTheme.typography.headlineSmall, viewModel::openById)

            if (note.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    note.tags.forEach { tag ->
                        AssistChip(
                            onClick = { viewModel.tag = tag; viewModel.query = ""; viewModel.back() },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            val planning = listOfNotNull(
                note.scheduled?.let { "Scheduled" to it },
                note.deadline?.let { "Deadline" to it },
                note.closed?.let { "Closed" to it },
            )
            if (planning.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    planning.forEach { (label, value) -> LabeledValue(label, value) }
                }
            }

            if (note.properties.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            note.properties.forEach { (name, value) -> LabeledValue(name, value) }
                        }
                    }
                }
            }

            if (note.content.isNotBlank()) {
                HorizontalDivider()
                LinkedText(note.content, MaterialTheme.typography.bodyLarge, viewModel::openById)
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Text where [[id:...]] links are shown by their description and open the target note. */
@Composable
private fun LinkedText(text: String, style: TextStyle, onOpenId: (String) -> Unit) {
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val annotated: AnnotatedString = remember(text, linkStyle) {
        buildAnnotatedString {
            var last = 0
            IdLinks.find(text).forEach { link ->
                append(text.substring(last, link.range.first))
                withLink(LinkAnnotation.Clickable(link.id, linkStyle) { onOpenId(link.id) }) {
                    append(link.description ?: link.id)
                }
                last = link.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(annotated, style = style)
}

private enum class Field { None, Title, Content }

private class PropertyRow(name: String, value: String) {
    var name by mutableStateOf(name)
    var value by mutableStateOf(value)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteEditor(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    note: OrgNote,
    keywords: Keywords,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()

    // The note as loaded when editing started; used to detect changes made by other apps
    var base by remember { mutableStateOf(note) }
    var title by remember { mutableStateOf(TextFieldValue(note.title)) }
    var state by remember { mutableStateOf(note.state) }
    var tags by remember { mutableStateOf(note.tags.joinToString(" ")) }
    val properties = remember { mutableStateListOf(*note.properties.map { PropertyRow(it.first, it.second) }.toTypedArray()) }
    var content by remember { mutableStateOf(TextFieldValue(note.content)) }
    var focused by remember { mutableStateOf(Field.None) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun currentEdit() = NoteEdit(
        title = title.text,
        state = state,
        tags = tags.split(Regex("[\\s:]+")).filter { it.isNotBlank() },
        properties = properties.map { it.name to it.value },
        content = content.text,
    )

    val dirty = currentEdit() != base.toEdit()

    fun close() {
        if (dirty) confirmDiscard = true else viewModel.setEditing(false)
    }

    BackHandler { close() }

    // "id:" completion in the focused field
    val active = when (focused) {
        Field.Title -> title
        Field.Content -> content
        Field.None -> null
    }
    val query = active?.takeIf { it.selection.collapsed }?.let { IdLinks.queryAt(it.text, it.selection.start) }
    val suggestions = remember(query?.text, library) {
        query?.let { IdLinks.suggestions(library.notes, it.text) }.orEmpty()
    }

    fun insertLink(target: OrgNote) {
        val q = query ?: return
        val field = focused
        scope.launch {
            val (id, updatedTarget) = viewModel.idFor(target) ?: return@launch
            if (updatedTarget.fileUri == base.fileUri && updatedTarget.index == base.index) {
                // Linked to itself: the ID was just added to this note
                base = updatedTarget
                properties.clear()
                properties.addAll(updatedTarget.properties.map { PropertyRow(it.first, it.second) })
            }
            val link = IdLinks.format(id, displayTitle(target.title))
            fun apply(v: TextFieldValue) =
                TextFieldValue(v.text.replaceRange(q.start, q.end, link), TextRange(q.start + link.length))
            when (field) {
                Field.Title -> title = apply(title)
                Field.Content -> content = apply(content)
                Field.None -> Unit
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Edit", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { close() }) { Icon(Icons.Default.Close, "Cancel") }
                },
                actions = {
                    IconButton(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                if (viewModel.save(base, currentEdit()) != null) viewModel.setEditing(false)
                                saving = false
                            }
                        },
                    ) { Icon(Icons.Default.Check, "Save") }
                },
            )
        },
        bottomBar = {
            if (suggestions.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(suggestions, key = { it.fileUri + "#" + it.index }) { target ->
                            ListItem(
                                headlineContent = { Text(displayTitle(target.title)) },
                                supportingContent = { Text(target.fileName) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state == null, onClick = { state = null }, label = { Text("None") })
                keywords.all.forEach { keyword ->
                    FilterChip(selected = state == keyword, onClick = { state = keyword }, label = { Text(keyword) })
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = TextFieldValue(it.text.replace("\n", " "), it.selection, it.composition) },
                label = { Text("Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) focused = Field.Title },
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (space separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focused = Field.None },
            )

            Text("Properties", style = MaterialTheme.typography.labelLarge)
            properties.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { row.name = it.replace(" ", "").uppercase() },
                        placeholder = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f).onFocusChanged { if (it.isFocused) focused = Field.None },
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = row.value,
                        onValueChange = { row.value = it },
                        placeholder = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f).onFocusChanged { if (it.isFocused) focused = Field.None },
                    )
                    IconButton(onClick = { properties.removeAt(i) }) { Icon(Icons.Default.Close, "Remove property") }
                }
            }
            TextButton(onClick = { properties.add(PropertyRow("", "")) }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add property")
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) focused = Field.Content },
            )

            Text(
                "Type id: in the title or content to link to another note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.setEditing(false) }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}
