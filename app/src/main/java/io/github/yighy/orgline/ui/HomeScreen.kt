package io.github.yighy.orgline.ui

import androidx.activity.compose.BackHandler
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
import io.github.yighy.orgline.data.NoteRepository

@Composable
fun WelcomeScreen(onPickFolder: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NotebookAvatar("Orgline")
            Spacer(Modifier.height(24.dp))
            Text("Orgline", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Pick the folder that holds your .org files. They stay where they are: " +
                    "Orgline reads and writes them directly.",
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(enabled = viewModel.searchActive) { viewModel.setSearch(false) }

    Scaffold(
        topBar = {
            val tasks = library.files.sumOf { it.openTasks }
            SearchableTopBar(
                title = "Notebooks",
                subtitle = if (tasks > 0) "$tasks to do" else null,
                navigationIcon = Icons.Default.Menu,
                navigationDescription = "Menu",
                onNavigation = onOpenDrawer,
                searchActive = viewModel.searchActive,
                onSearchActiveChange = viewModel::setSearch,
                query = viewModel.query,
                onQueryChange = { viewModel.query = it },
                searchPlaceholder = "Search all notes",
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newNotebook = true }) { Icon(Icons.Default.Add, "New notebook") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            library.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            when {
                viewModel.searchActive -> SearchResults(viewModel, library, library.notes, library.tags)

                library.files.isEmpty() && library.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

                library.files.isEmpty() ->
                    Text(
                        "No notebooks yet. Tap + to create one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )

                else -> PullToRefresh(viewModel) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        // On the list rather than the Scaffold, so pulling down expands the app bar
                        // before pull-to-refresh takes the rest of the gesture
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        items(library.files, key = { it.uri }) { file ->
                            NotebookCard(file, onClick = { viewModel.openNotebook(file.uri) }, modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
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

@Composable
private fun NotebookCard(file: NoteRepository.LoadedFile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = NotebookCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .sharedContainer("notebook-${file.uri}", NotebookCardShape)
            .fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            NotebookAvatar(file.title)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(file.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val counts = buildList {
                    add(if (file.notes.size == 1) "1 note" else "${file.notes.size} notes")
                    if (file.openTasks > 0) add("${file.openTasks} to do")
                }
                Text(
                    counts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
