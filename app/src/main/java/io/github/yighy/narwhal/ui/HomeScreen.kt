package io.github.yighy.narwhal.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Face
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yighy.narwhal.data.NoteRepository

@Composable
fun WelcomeScreen(onPickFolder: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(96.dp)
            Spacer(Modifier.height(24.dp))
            Text("Narwhal", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Pick the folder that holds your .org files. They stay where they are: " +
                    "Narwhal reads and writes them directly.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onPickFolder, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)) {
                Text("Choose folder", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    snackbar: SnackbarHostState,
    onOpenDrawer: () -> Unit,
) {
    var newNotebook by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickIcon by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<NoteRepository.LoadedFile?>(null) }
    var selectionMenu by remember { mutableStateOf(false) }
    val icons by viewModel.notebookIcons.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val selected = viewModel.selectedNotebooks
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = viewModel.searchActive) { viewModel.setSearch(false) }
    BackHandler(enabled = selecting) { viewModel.clearNotebookSelection() }

    Scaffold(
        topBar = {
            val selectionBar: @Composable () -> Unit = {
                TopAppBar(
                    colors = blendedTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearNotebookSelection) { Icon(Icons.Default.Close, "Clear selection") }
                    },
                    title = { Text("${selected.size} selected") },
                    actions = {
                        // One notebook at a time
                        if (selected.size == 1) {
                            IconButton(onClick = { pickIcon = selected.first() }) { Icon(Icons.Default.Face, "Change icon") }
                        }
                        Box {
                            IconButton(onClick = { selectionMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = selectionMenu, onDismissRequest = { selectionMenu = false }) {
                                if (selected.size == 1) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { selectionMenu = false; renaming = library.file(selected.first()) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { selectionMenu = false; confirmDelete = true },
                                )
                            }
                        }
                    },
                )
            }
            val tasks = library.files.sumOf { it.openTasks }
            TopBarWithSelection(selecting, selectionBar) {
                SearchableTopBar(
                    title = "Notebooks",
                    subtitle = if (tasks > 0) {
                        { CountsSubtitle(notes = null, todo = tasks, onTodoClick = { viewModel.searchFor("it.todo") }) }
                    } else {
                        null
                    },
                    navigationIcon = Icons.Default.Menu,
                    navigationDescription = "Menu",
                    onNavigation = onOpenDrawer,
                    searchActive = viewModel.searchActive,
                    onSearchActiveChange = viewModel::setSearch,
                    query = viewModel.query,
                    onQueryChange = { viewModel.query = it },
                    searchPlaceholder = "Search all notes",
                    scrollBehavior = scrollBehavior,
                    onSaveSearch = { viewModel.saveCurrentSearch(null) }
                        .takeIf { viewModel.query.isNotBlank() || viewModel.tag != null },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = { newNotebook = true }) { Icon(Icons.Default.Add, "New notebook") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            library.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            when {
                viewModel.searchActive -> SearchResults(viewModel, library, library.notes)

                library.files.isEmpty() && library.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

                library.files.isEmpty() ->
                    Text(
                        "No notebooks yet. Tap + to create one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )

                else -> PullToRefresh(viewModel) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        // On the list rather than the Scaffold, so pulling down expands the app bar
                        // before pull-to-refresh takes the rest of the gesture
                        modifier = Modifier
                            .fillMaxSize()
                            // A swipe during the screen transition cuts it short
                            .cutTransitionOnScroll()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        items(library.files, key = { it.uri }) { file ->
                            NotebookCard(
                                file = file,
                                icon = icons[file.uri],
                                selected = file.uri in selected,
                                onClick = {
                                    if (selecting) viewModel.toggleNotebookSelected(file.uri) else viewModel.openNotebook(file.uri)
                                },
                                onLongClick = { viewModel.toggleNotebookSelected(file.uri) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val names = library.files.filter { it.uri in selected }.map { it.ref.path }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (names.size == 1) "Delete this notebook?" else "Delete ${names.size} notebooks?") },
            text = { Text("These files are deleted from the folder, with all their notes:\n" + names.joinToString("\n") { "• $it" }) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteSelectedNotebooks() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    pickIcon?.let { uri ->
        IconDialog(
            current = icons[uri],
            onDismiss = { pickIcon = null },
            onSelect = { emoji ->
                pickIcon = null
                viewModel.setNotebookIcon(uri, emoji)
                viewModel.clearNotebookSelection()
            },
        )
    }

    renaming?.let { file ->
        TextInputDialog(
            title = "Rename notebook",
            label = "Name",
            confirmLabel = "Rename",
            initial = file.title,
            onDismiss = { renaming = null },
            onConfirm = { renaming = null; viewModel.renameSelectedNotebook(it) },
        )
    }

    if (newNotebook) {
        TextInputDialog(
            title = "New notebook",
            label = "Name",
            suffix = ".org",
            confirmLabel = "Create",
            onDismiss = { newNotebook = false },
            onConfirm = { newNotebook = false; viewModel.createNotebook(it) },
        )
    }
}

val NotebookCardShape = RoundedCornerShape(28.dp)

/** The notebook's name moves from its card into the notebook's app bar. */
fun notebookTitleKey(fileUri: String) = "notebook-title-$fileUri"

/** Notebook card; long press selects it (a check replaces the avatar). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(
    file: NoteRepository.LoadedFile,
    icon: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = NotebookCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .sharedContainer("notebook-${file.uri}", NotebookCardShape)
            .fillMaxWidth()
            .clip(NotebookCardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary) }
            } else {
                NotebookAvatar(file.title, icon)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.sharedText(notebookTitleKey(file.uri)),
                )
                val counts = buildList {
                    add(if (file.notes.size == 1) "1 note" else "${file.notes.size} notes")
                    if (file.openTasks > 0) add("${file.openTasks} to do")
                }
                Text(
                    counts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.sharedText(subtitleKey(notebookTitleKey(file.uri))),
                )
                file.preface.lineSequence().firstOrNull { it.isNotBlank() }?.let {
                    Text(
                        it.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
