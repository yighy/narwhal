package io.github.yighy.orgline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.NoteTree
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.core.Placement
import io.github.yighy.orgline.data.NoteRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    file: NoteRepository.LoadedFile,
    snackbar: SnackbarHostState,
) {
    // Raw preface being edited in place, or null
    var prefaceDraft by remember { mutableStateOf<TextFieldValue?>(null) }
    val currentFile by rememberUpdatedState(file)

    /** Saves the preface being edited, if it changed (no save button). */
    fun commitPreface() {
        val draft = prefaceDraft ?: return
        prefaceDraft = null
        if (draft.text != currentFile.doc.preface.trimEnd()) viewModel.setPreface(currentFile, draft.text)
    }
    /** Edits the preface, with the cursor at [cursor] (the end by default). */
    fun editPreface(cursor: Int? = null) {
        val raw = file.doc.preface.trimEnd()
        prefaceDraft = TextFieldValue(raw, TextRange((cursor ?: raw.length).coerceIn(0, raw.length)))
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by viewModel.settings.collectAsState()

    val selection = viewModel.selection?.takeIf { it.fileUri == file.uri }
    val selecting = selection != null

    BackHandler(enabled = viewModel.searchActive) { viewModel.setSearch(false) }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = prefaceDraft != null) { commitPreface() }
    DisposableEffect(Unit) { onDispose { commitPreface() } }
    // Closing the keyboard ends the preface editing (and saves it)
    ClearFocusWhenKeyboardHides()

    Scaffold(
        modifier = Modifier
            .sharedContainer("notebook-${file.uri}", NotebookCardShape)
            .imePadding(),
        topBar = {
            if (selecting) {
                SelectionTopBar(viewModel, library, file)
            } else {
                SearchableTopBar(
                    title = file.title,
                    subtitle = {
                        CountsSubtitle(file.notes.size, file.openTasks, onTodoClick = { viewModel.searchFor("it.todo") })
                    },
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    navigationDescription = "Back",
                    onNavigation = { viewModel.back() },
                    searchActive = viewModel.searchActive,
                    onSearchActiveChange = viewModel::setSearch,
                    query = viewModel.query,
                    onQueryChange = { viewModel.query = it },
                    searchPlaceholder = "Search in ${file.title}",
                    scrollBehavior = scrollBehavior,
                    extraActions = { FoldAllButton(viewModel, file.notes) },
                    sharedTitleKey = notebookTitleKey(file.uri),
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text("Edit preface") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { dismiss(); editPreface() },
                    )

                }
            }
        },
        floatingActionButton = {
            if (prefaceDraft == null && !selecting) {
                FloatingActionButton(onClick = { viewModel.startNewNote(file.uri) }) { Icon(Icons.Default.Add, "New note") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.searchActive) {
                SearchResults(viewModel, library, file.notes, file.notes.flatMap { it.tags }.distinct().sorted())
            } else {
                NotebookOutline(
                    viewModel = viewModel,
                    file = file,
                    prefaceDraft = prefaceDraft,
                    // Only while editing: the keyboard can still update the field as it goes away
                    onPrefaceDraftChange = { if (prefaceDraft != null) prefaceDraft = it },
                    onCommitPreface = ::commitPreface,
                    onEditPreface = { editPreface(it) },
                    selected = selection?.indices.orEmpty(),
                    showContent = settings.showContentInNotebook,
                    scrollConnection = scrollBehavior.nestedScrollConnection,
                    onAddNear = { note, placement -> viewModel.startNewNote(file.uri, note, placement) },
                )
            }
            if (selecting) {
                SelectionToolbar(viewModel, file, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
            }
        }
    }


}

@Composable
private fun FoldAllButton(viewModel: AppViewModel, notes: List<OrgNote>) {
    if (notes.indices.none { NoteTree.hasChildren(notes, it) }) return
    val anyExpanded = notes.indices.any { i -> NoteTree.hasChildren(notes, i) && !viewModel.isFolded(notes[i]) }
    IconButton(onClick = { viewModel.setAllFolded(notes, anyExpanded) }) {
        Icon(
            if (anyExpanded) OrglineIcons.UnfoldLess else OrglineIcons.UnfoldMore,
            if (anyExpanded) "Collapse all" else "Expand all",
        )
    }
}

/** Preface (editable in place) and the foldable outline of notes. */
@Composable
private fun NotebookOutline(
    viewModel: AppViewModel,
    file: NoteRepository.LoadedFile,
    prefaceDraft: TextFieldValue?,
    onPrefaceDraftChange: (TextFieldValue) -> Unit,
    onCommitPreface: () -> Unit,
    onEditPreface: (cursor: Int?) -> Unit,
    selected: Set<Int>,
    showContent: Boolean,
    scrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    onAddNear: (OrgNote, Placement) -> Unit,
) {
    val shown = NoteTree.visible(file.notes, viewModel::isFolded).map { it.index }.toSet()
    val ancestorLevels = remember(file.notes) { NoteTree.ancestorLevels(file.notes) }
    val selecting = selected.isNotEmpty()

    PullToRefresh(viewModel) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            // On the list rather than the Scaffold, so pulling down expands the app bar
            // before pull-to-refresh takes the rest of the gesture
            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
        ) {
            item(key = "header") {
                val prefaceStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prefaceDraft != null) {
                    InPlaceField(
                        value = prefaceDraft,
                        onValueChange = onPrefaceDraftChange,
                        style = prefaceStyle,
                        placeholder = "Text before the first note. Lines like #+TITLE: set the notebook's name.",
                        onFocusLost = onCommitPreface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                } else if (file.preface.isNotBlank()) {
                    // The raw preface (shown as is, without its #+ lines) is edited from the
                    // end, or from where the text was tapped when it is shown the same
                    val shownAsRaw = file.preface == file.doc.preface.trimEnd()
                    LinkedText(
                        file.preface,
                        prefaceStyle,
                        viewModel::openById,
                        Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = null, indication = null) { onEditPreface(null) }
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        onTextTap = { onEditPreface(if (shownAsRaw) it else null) },
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
                        hasChildren = NoteTree.hasChildren(file.notes, note.index),
                        folded = viewModel.isFolded(note),
                        showContent = showContent,
                        selected = note.index in selected,
                        selecting = selecting,
                        swipeOpen = viewModel.openSwipeRow,
                        onSwipeOpenChange = { open ->
                            viewModel.openSwipeRow = if (open) rowKey else viewModel.openSwipeRow.takeIf { it != rowKey }
                        },
                        onToggleDone = { viewModel.toggleDone(note) },
                        onToggleFold = { viewModel.toggleFold(note) },
                        onClick = {
                            when {
                                selecting -> viewModel.toggleSelected(note)
                                // A tap while a row shows its actions just closes it
                                viewModel.openSwipeRow != null -> viewModel.openSwipeRow = null
                                else -> viewModel.openNote(note)
                            }
                        },
                        onLongClick = {
                            viewModel.openSwipeRow = null
                            viewModel.toggleSelected(note)
                        },
                        onOpenId = viewModel::openById,
                        onAddNear = { placement -> onAddNear(note, placement) },
                    )
                }
            }
        }
    }
}

/**
 * One note in the notebook outline: fold chevron (or bullet), then the note.
 * Swipe left for new-note actions, swipe right to toggle TODO / DONE, long press to select.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineRow(
    note: OrgNote,
    ancestorLevels: List<Int>,
    hasChildren: Boolean,
    folded: Boolean,
    showContent: Boolean,
    selected: Boolean,
    selecting: Boolean,
    swipeOpen: String?,
    onSwipeOpenChange: (Boolean) -> Unit,
    onToggleDone: () -> Unit,
    onToggleFold: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenId: (String) -> Unit,
    onAddNear: (Placement) -> Unit,
) {
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
        actions = Placement.entries.map { placement ->
            SwipeAction(placementIcon(placement), newNoteTitle(placement)) { onAddNear(placement) }
        },
        modifier = Modifier.sharedContainer("note-$key", RectangleShape),
        enabled = !selecting,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
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
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = 52.dp)
                .padding(start = indent(note.level), end = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (hasChildren) {
                IconButton(onClick = onToggleFold) {
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
                // Title and content move into the note screen when the note opens
                NoteLine(
                    note,
                    subtitle = null,
                    onClick = null,
                    onOpenId = onOpenId,
                    titleModifier = Modifier.sharedText(noteTitleKey(note)),
                )
                if (showContent && note.content.isNotBlank()) {
                    LinkedText(
                        note.content.trimIndent(),
                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        onOpenId,
                        Modifier.padding(top = 4.dp).sharedText(noteContentKey(note)),
                    )
                }
            }
        }
    }
}

/** Start padding of a note at [level] in the outline. */
private fun indent(level: Int) = 4.dp + 24.dp * (level - 1).coerceIn(0, 6)

/** Horizontal position of the guide line under the chevron of a note at [level]. */
private fun guideX(level: Int) = indent(level) + 24.dp
