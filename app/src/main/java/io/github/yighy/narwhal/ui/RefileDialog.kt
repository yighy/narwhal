package io.github.yighy.narwhal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yighy.narwhal.R
import io.github.yighy.narwhal.core.OrgNote
import io.github.yighy.narwhal.data.NoteRepository

/**
 * Where to move [moving] notes: a notebook, then its end or a note to go under (as its last
 * sub-note). The notes moved and their sub-notes are not offered.
 */
@Composable
fun RefileDialog(
    library: NoteRepository.Library,
    moving: List<OrgNote>,
    onDismiss: () -> Unit,
    onConfirm: (fileUri: String, parent: OrgNote?) -> Unit,
) {
    var notebook by remember { mutableStateOf<String?>(null) }
    val file = notebook?.let(library::file)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (file == null) {
                Text(stringResource(R.string.move_to))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { notebook = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    Text(file.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                if (file == null) {
                    items(library.files, key = { it.uri }) { f -> Choice(f.title, onClick = { notebook = f.uri }) }
                } else {
                    item(key = "end") { Choice(stringResource(R.string.at_end_of_notebook), bold = true, onClick = { onConfirm(file.uri, null) }) }
                    val excluded = excluded(file, moving)
                    items(file.notes.filter { it.index !in excluded }, key = { it.index }) { note ->
                        Choice(displayTitle(note.title), indent = note.level - 1, onClick = { onConfirm(file.uri, note) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Choice(text: String, onClick: () -> Unit, indent: Int = 0, bold: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = (12 + 16 * indent).dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    )
}

/** Indices of [file]'s notes that are moving, with their sub-notes. */
private fun excluded(file: NoteRepository.LoadedFile, moving: List<OrgNote>): Set<Int> = buildSet {
    moving.filter { it.fileUri == file.uri }.forEach { m ->
        add(m.index)
        var i = m.index + 1
        while (i < file.notes.size && file.notes[i].level > m.level) add(i++)
    }
}
