package io.github.yighy.orgline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.IdLinks
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.core.Placement
import io.github.yighy.orgline.data.NoteRepository
import kotlinx.coroutines.launch

/**
 * Screen of a note being created: only its title can be typed. The note is written to the
 * file when the title is saved; leaving without a title creates nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    screen: Screen.NewNote,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun cancel() {
        if (title.text.isNotBlank()) confirmDiscard = true else viewModel.back()
    }

    fun save() {
        if (saving) return
        if (title.text.isBlank()) {
            viewModel.message("Type a title to create the note")
            return
        }
        saving = true
        viewModel.finishNewNote(screen, title.text)
    }

    BackHandler { cancel() }

    // "id:" completion in the title, as on the note screen
    val query = title.takeIf { it.selection.collapsed }?.let { IdLinks.queryAt(it.text, it.selection.start) }
    val suggestions = remember(query?.text, library) {
        query?.let { IdLinks.suggestions(library.notes, it.text) }.orEmpty()
    }

    fun insertLink(target: OrgNote) {
        val q = query ?: return
        scope.launch {
            val (id, _) = viewModel.idFor(target) ?: return@launch
            val link = IdLinks.format(id, displayTitle(target.title))
            title = TextFieldValue(title.text.replaceRange(q.start, q.end, link), TextRange(q.start + link.length))
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {},
                colors = blendedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = ::cancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") }
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
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                listOfNotNull(library.file(screen.fileUri)?.title, positionLabel(screen)).joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            DraftField(
                value = title,
                onValueChange = { title = TextFieldValue(it.text.replace("\n", " "), it.selection, it.composition) },
                label = "Title",
                singleLine = false,
                onSave = ::save,
                onCancel = ::cancel,
            )
            Text(
                "The note is created when you save its title. Type id: to link to another note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this note?") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.back() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

/** Where the new note goes, e.g. "sub-note of Design". */
private fun positionLabel(screen: Screen.NewNote): String {
    val anchor = screen.anchor?.let { displayTitle(it.title) } ?: return "new note at the end"
    return when (screen.placement) {
        Placement.Above -> "new note above $anchor"
        Placement.Under -> "sub-note of $anchor"
        Placement.Below, null -> "new note below $anchor"
    }
}
