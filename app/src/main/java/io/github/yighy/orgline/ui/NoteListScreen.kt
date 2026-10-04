package io.github.yighy.orgline.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.NoteSearch
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.data.NoteRepository

@Composable
fun WelcomeScreen(onPickFolder: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Orgline", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.heightIn(min = 12.dp))
            Text(
                "Pick the folder that holds your .org files. They stay where they are: " +
                    "Orgline reads and writes them directly.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.heightIn(min = 24.dp))
            Button(onClick = onPickFolder) { Text("Choose folder") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    snackbar: SnackbarHostState,
    onPickFolder: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var newNoteOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orgline") },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Reload") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Change folder") },
                                onClick = { menuOpen = false; onPickFolder() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newNoteOpen = true }) { Icon(Icons.Default.Add, "New note") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = { viewModel.query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (viewModel.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                    }
                },
                singleLine = true,
            )

            if (library.tags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(library.tags) { tag ->
                        FilterChip(
                            selected = viewModel.tag == tag,
                            onClick = { viewModel.tag = if (viewModel.tag == tag) null else tag },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            if (library.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            library.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            val filtering = viewModel.query.isNotBlank() || viewModel.tag != null

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                if (filtering) {
                    val results = library.notes.filter { NoteSearch.matches(it, viewModel.query, viewModel.tag) }
                    if (results.isEmpty()) {
                        item { Text("No matching notes", Modifier.padding(16.dp)) }
                    }
                    items(results, key = { it.fileUri + "#" + it.index }) { note ->
                        NoteRow(note, indent = false, showFile = true, onClick = { viewModel.open(note) })
                    }
                } else {
                    if (library.files.isEmpty() && !library.loading) {
                        item { Text("No .org files in this folder yet. Tap + to create a note.", Modifier.padding(16.dp)) }
                    }
                    library.files.forEach { file ->
                        stickyHeader(key = file.ref.uri.toString()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    file.ref.path,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(file.notes, key = { it.fileUri + "#" + it.index }) { note ->
                            NoteRow(note, indent = true, showFile = false, onClick = { viewModel.open(note) })
                        }
                    }
                }
            }
        }
    }

    if (newNoteOpen) {
        NewNoteDialog(
            library = library,
            onDismiss = { newNoteOpen = false },
            onCreate = { fileUri, newFileName, title ->
                newNoteOpen = false
                viewModel.createNote(fileUri, newFileName, title)
            },
        )
    }
}

@Composable
fun NoteRow(note: OrgNote, indent: Boolean, showFile: Boolean, onClick: () -> Unit) {
    val start = if (indent) 16.dp + 16.dp * (note.level - 1).coerceAtMost(6) else 16.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = start, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            note.state?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (note.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
            }
            note.priority?.let {
                Text("#$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                displayTitle(note.title),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                color = if (note.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        }

        val details = buildList {
            if (note.tags.isNotEmpty()) add(note.tags.joinToString(" ") { "#$it" })
            note.scheduled?.let { add("Scheduled $it") }
            note.deadline?.let { add("Deadline $it") }
            if (showFile) add(note.fileName)
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun NewNoteDialog(
    library: NoteRepository.Library,
    onDismiss: () -> Unit,
    onCreate: (fileUri: String?, newFileName: String?, title: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf(library.files.firstOrNull()?.ref?.uri?.toString()) }
    var newFileName by remember { mutableStateOf("inbox") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.heightIn(min = 12.dp))
                if (library.files.isEmpty()) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("New file") },
                        suffix = { Text(".org") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("File", style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        library.files.forEach { file ->
                            val uri = file.ref.uri.toString()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = fileUri == uri, onClick = { fileUri = uri }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = fileUri == uri, onClick = { fileUri = uri })
                                Text(file.ref.path)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (fileUri != null || newFileName.isNotBlank()),
                onClick = { onCreate(fileUri, newFileName.trim(), title) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
