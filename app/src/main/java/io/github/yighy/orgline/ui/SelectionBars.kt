package io.github.yighy.orgline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.yighy.orgline.core.Placement
import io.github.yighy.orgline.data.NoteRepository

/** App bar while notes are selected: cut, copy, paste, move, and delete in the overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(viewModel: AppViewModel, library: NoteRepository.Library, file: NoteRepository.LoadedFile) {
    val count = viewModel.selection?.indices?.size ?: 0
    val single = count == 1
    var pasteMenu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickNotebook by remember { mutableStateOf(false) }

    TopAppBar(
        colors = blendedTopBarColors(),
        navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Default.Close, "Clear selection") }
        },
        title = { Text("$count selected") },
        actions = {
            IconButton(onClick = viewModel::cutSelection) { Icon(OrglineIcons.Cut, "Cut") }
            IconButton(onClick = viewModel::copySelection) { Icon(OrglineIcons.Copy, "Copy") }
            if (single && viewModel.clipboard.isNotEmpty()) {
                Box {
                    IconButton(onClick = { pasteMenu = true }) { Icon(OrglineIcons.Paste, "Paste") }
                    DropdownMenu(expanded = pasteMenu, onDismissRequest = { pasteMenu = false }) {
                        Placement.entries.forEach { placement ->
                            DropdownMenuItem(
                                text = { Text(pasteLabel(placement)) },
                                leadingIcon = { Icon(placementIcon(placement), null) },
                                onClick = { pasteMenu = false; viewModel.pasteAtSelection(placement) },
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { moveMenu = true }) { Icon(OrglineIcons.Move, "Move") }
                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = single,
                        onClick = { moveMenu = false; viewModel.moveSelection(AppViewModel.Move.Up) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = single,
                        onClick = { moveMenu = false; viewModel.moveSelection(AppViewModel.Move.Down) },
                    )
                    DropdownMenuItem(
                        text = { Text("Promote (one level up)") },
                        onClick = { moveMenu = false; viewModel.moveSelection(AppViewModel.Move.Promote) },
                    )
                    DropdownMenuItem(
                        text = { Text("Demote (one level down)") },
                        onClick = { moveMenu = false; viewModel.moveSelection(AppViewModel.Move.Demote) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to notebook…") },
                        enabled = library.files.size > 1,
                        onClick = { moveMenu = false; pickNotebook = true },
                    )
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = { moreMenu = false; viewModel.selectAll(file.notes) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { moreMenu = false; confirmDelete = true },
                    )
                }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (single) "Delete this note?" else "Delete $count notes?") },
            text = { Text("Their sub-notes are deleted too.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteSelection() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (pickNotebook) {
        val targets = library.files.filter { it.uri != file.uri }
        ChoiceDialog(
            title = "Move to notebook",
            options = targets.map { it.title },
            selected = null,
            onDismiss = { pickNotebook = false },
            onSelect = { title ->
                pickNotebook = false
                targets.firstOrNull { it.title == title }?.let { viewModel.moveSelectionTo(it.uri) }
            },
        )
    }
}

private sealed interface ToolbarDialog {
    data object Scheduled : ToolbarDialog
    data object Deadline : ToolbarDialog
    data object State : ToolbarDialog
    data class NewNote(val placement: Placement) : ToolbarDialog
}

/** Floating toolbar at the bottom while notes are selected: planning, state, done, new note. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionToolbar(viewModel: AppViewModel, file: NoteRepository.LoadedFile, modifier: Modifier = Modifier) {
    val selected = viewModel.selectedNotes()
    val single = selected.singleOrNull()
    var dialog by remember { mutableStateOf<ToolbarDialog?>(null) }
    var newMenu by remember { mutableStateOf(false) }

    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        IconButton(onClick = { dialog = ToolbarDialog.Scheduled }) { Icon(OrglineIcons.Scheduled, "Schedule") }
        IconButton(onClick = { dialog = ToolbarDialog.Deadline }) { Icon(OrglineIcons.Deadline, "Deadline") }
        IconButton(onClick = { dialog = ToolbarDialog.State }) { Icon(OrglineIcons.State, "State") }
        IconButton(onClick = viewModel::toggleDoneOfSelection) { Icon(Icons.Default.Check, "Done") }
        if (single != null) {
            Box {
                IconButton(onClick = { newMenu = true }) { Icon(Icons.Default.Add, "New note") }
                DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                    Placement.entries.forEach { placement ->
                        DropdownMenuItem(
                            text = { Text(newNoteTitle(placement)) },
                            leadingIcon = { Icon(placementIcon(placement), null) },
                            onClick = { newMenu = false; dialog = ToolbarDialog.NewNote(placement) },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        ToolbarDialog.Scheduled -> TimestampDialog(
            initial = single?.scheduled,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.setScheduledOfSelection(it) },
        )
        ToolbarDialog.Deadline -> TimestampDialog(
            initial = single?.deadline,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.setDeadlineOfSelection(it) },
        )
        ToolbarDialog.State -> ChoiceDialog(
            title = "State",
            options = listOf(null) + file.doc.keywords.all,
            selected = single?.state,
            onDismiss = { dialog = null },
            onSelect = { dialog = null; viewModel.setStateOfSelection(it) },
        )
        is ToolbarDialog.NewNote -> TextInputDialog(
            title = newNoteTitle(d.placement),
            label = "Title",
            confirmLabel = "Create",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.createNoteNearSelection(d.placement, it) },
        )
    }
}

fun placementIcon(placement: Placement) = when (placement) {
    Placement.Above -> OrglineIcons.NewAbove
    Placement.Under -> OrglineIcons.SubNote
    Placement.Below -> OrglineIcons.NewBelow
}

private fun pasteLabel(placement: Placement) = when (placement) {
    Placement.Above -> "Paste above"
    Placement.Under -> "Paste as sub-notes"
    Placement.Below -> "Paste below"
}
