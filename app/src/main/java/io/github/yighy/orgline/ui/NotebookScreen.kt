package io.github.yighy.orgline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.NoteTree
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.data.NoteRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    file: NoteRepository.LoadedFile,
    snackbar: SnackbarHostState,
) {
    var newNote by remember { mutableStateOf(false) }
    // Raw preface being edited in place, or null
    var prefaceDraft by remember { mutableStateOf<TextFieldValue?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    fun editPreface() {
        val raw = file.doc.preface.trimEnd()
        prefaceDraft = TextFieldValue(raw, TextRange(raw.length))
    }
    // Note to add a child (true) or a sibling (false) to, from the long-press menu
    var addNear by remember { mutableStateOf<Pair<OrgNote, Boolean>?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by viewModel.settings.collectAsState()

    BackHandler(enabled = viewModel.searchActive) { viewModel.setSearch(false) }
    BackHandler(enabled = prefaceDraft != null) {
        if (prefaceDraft?.text != file.doc.preface.trimEnd()) confirmDiscard = true else prefaceDraft = null
    }

    Scaffold(
        modifier = Modifier
            .sharedContainer("notebook-${file.uri}", NotebookCardShape)
            .imePadding(),
        topBar = {
            SearchableTopBar(
                title = file.title,
                subtitle = buildList {
                    add(if (file.notes.size == 1) "1 note" else "${file.notes.size} notes")
                    if (file.openTasks > 0) add("${file.openTasks} to do")
                }.joinToString(" · "),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationDescription = "Back",
                onNavigation = { viewModel.back() },
                searchActive = viewModel.searchActive,
                onSearchActiveChange = viewModel::setSearch,
                query = viewModel.query,
                onQueryChange = { viewModel.query = it },
                searchPlaceholder = "Search in ${file.title}",
                scrollBehavior = scrollBehavior,
                extraActions = {
                    val anyExpanded = file.notes.indices.any { i ->
                        NoteTree.hasChildren(file.notes, i) && !viewModel.isFolded(file.notes[i])
                    }
                    if (file.notes.indices.any { NoteTree.hasChildren(file.notes, it) }) {
                        IconButton(onClick = { viewModel.setAllFolded(file.notes, anyExpanded) }) {
                            Icon(
                                if (anyExpanded) OrglineIcons.UnfoldLess else OrglineIcons.UnfoldMore,
                                if (anyExpanded) "Collapse all" else "Expand all",
                            )
                        }
                    }
                },
            ) { dismiss ->
                DropdownMenuItem(
                    text = { Text("Edit preface") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { dismiss(); editPreface() },
                )
            }
        },
        floatingActionButton = {
            if (prefaceDraft == null) {
                FloatingActionButton(onClick = { newNote = true }) { Icon(Icons.Default.Add, "New note") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.searchActive) {
                SearchResults(viewModel, library, file.notes, file.notes.flatMap { it.tags }.distinct().sorted())
                return@Column
            }

            val shown = NoteTree.visible(file.notes, viewModel::isFolded).map { it.index }.toSet()
            val ancestorLevels = remember(file.notes) { NoteTree.ancestorLevels(file.notes) }
            PullToRefresh(viewModel) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    // On the list rather than the Scaffold, so pulling down expands the app bar
                    // before pull-to-refresh takes the rest of the gesture
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    item(key = "header") {
                        val draft = prefaceDraft
                        if (draft != null) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                DraftField(
                                    value = draft,
                                    onValueChange = { prefaceDraft = it },
                                    label = "Preface",
                                    singleLine = false,
                                    minLines = 4,
                                    onSave = { viewModel.setPreface(file, draft.text); prefaceDraft = null },
                                    onCancel = { prefaceDraft = null },
                                )
                                Text(
                                    "Text before the first note. Lines like #+TITLE: set the notebook's name.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        } else if (file.preface.isNotBlank()) {
                            LinkedText(
                                file.preface,
                                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                viewModel::openById,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editPreface() }
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                    if (file.notes.isEmpty()) {
                        item {
                            Text(
                                "No notes yet. Tap + to add one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    // Every note stays in the list; folded ones collapse to zero height so
                    // that folding and unfolding animate like an outline
                    items(file.notes, key = { it.fileUri + "#" + it.index }) { note ->
                        AnimatedVisibility(
                            visible = note.index in shown,
                            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        ) {
                            val rowKey = note.fileUri + "#" + note.index
                            OutlineRow(
                                note = note,
                                ancestorLevels = ancestorLevels[note.index],
                                swipeOpen = viewModel.openSwipeRow,
                                onSwipeOpenChange = { open ->
                                    viewModel.openSwipeRow = if (open) rowKey else viewModel.openSwipeRow.takeIf { it != rowKey }
                                },
                                onToggleDone = { viewModel.toggleDone(note) },
                                hasChildren = NoteTree.hasChildren(file.notes, note.index),
                                folded = viewModel.isFolded(note),
                                showContent = settings.showContentInNotebook,
                                onToggle = { viewModel.toggleFold(note) },
                                onClick = {
                                    // A tap while a row shows its actions just closes it
                                    if (viewModel.openSwipeRow != null) viewModel.openSwipeRow = null else viewModel.openNote(note)
                                },
                                onOpenId = viewModel::openById,
                                onAddChild = { addNear = note to true },
                                onAddSibling = { addNear = note to false },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; prefaceDraft = null }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (newNote) {
        TextInputDialog(
            title = "New note",
            label = "Title",
            confirmLabel = "Create",
            onDismiss = { newNote = false },
            onConfirm = { newNote = false; viewModel.createNote(file.uri, it) },
        )
    }

    addNear?.let { (anchor, asChild) ->
        TextInputDialog(
            title = if (asChild) "New sub-note" else "New note after",
            label = "Title",
            confirmLabel = "Create",
            onDismiss = { addNear = null },
            onConfirm = { addNear = null; viewModel.createNoteNear(anchor, asChild, it) },
        )
    }
}

/**
 * One note in the notebook outline: fold chevron (or bullet), then the note.
 * Swipe left shows actions, swipe right toggles TODO / DONE; long press opens the same actions as a menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineRow(
    note: OrgNote,
    ancestorLevels: List<Int>,
    swipeOpen: String?,
    onSwipeOpenChange: (Boolean) -> Unit,
    onToggleDone: () -> Unit,
    hasChildren: Boolean,
    folded: Boolean,
    showContent: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onOpenId: (String) -> Unit,
    onAddChild: () -> Unit,
    onAddSibling: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (folded) -90f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )

    val key = note.fileUri + "#" + note.index
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    SwipeRow(
        key = key,
        openKey = swipeOpen,
        onOpenChange = onSwipeOpenChange,
        swipeRightIcon = if (note.isDone) OrglineIcons.Undo else OrglineIcons.Check,
        onSwipeRight = onToggleDone,
        actions = listOf(
            SwipeAction(OrglineIcons.SubNote, "Add sub-note", onAddChild),
            SwipeAction(Icons.Default.Add, "Add note after", onAddSibling),
        ),
        modifier = Modifier.sharedContainer("note-$key", RectangleShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Vertical guides: one per expanded ancestor, and one under this note's
                    // chevron when it is expanded, so they join into lines along the subtree
                    val stroke = 1.5.dp.toPx()
                    ancestorLevels.forEach { level ->
                        val x = guideX(level).toPx()
                        drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), stroke)
                    }
                    if (hasChildren && !folded) {
                        val x = guideX(note.level).toPx()
                        drawLine(guideColor, Offset(x, 40.dp.toPx()), Offset(x, size.height), stroke)
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .heightIn(min = 52.dp)
                .padding(start = indent(note.level), end = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (hasChildren) {
                IconButton(onClick = onToggle) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        if (folded) "Expand" else "Collapse",
                        Modifier.rotate(chevronRotation),
                    )
                }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                NoteLine(note, subtitle = null, onClick = null)
                if (showContent && note.content.isNotBlank()) {
                    LinkedText(
                        note.content.trimIndent(),
                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        onOpenId,
                        Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Add sub-note") }, onClick = { menuOpen = false; onAddChild() })
            DropdownMenuItem(text = { Text("Add note after") }, onClick = { menuOpen = false; onAddSibling() })
        }
    }
}

/** Start padding of a note at [level] in the outline. */
private fun indent(level: Int) = 4.dp + 24.dp * (level - 1).coerceIn(0, 6)

/** Horizontal position of the guide line under the chevron of a note at [level]. */
private fun guideX(level: Int) = indent(level) + 24.dp
