package io.github.yighy.orgline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Saved searches: a tap runs one over all notebooks, the pencil edits or deletes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchesScreen(viewModel: AppViewModel) {
    val searches by viewModel.savedSearches.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Searches") },
                colors = blendedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            if (searches.isEmpty()) {
                item {
                    Text(
                        "No saved searches. Save one with the bookmark button while searching.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(searches) { index, search ->
                ListItem(
                    headlineContent = { Text(search.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(search.query, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Default.Search, null) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.editSavedSearch(index) }) { Icon(Icons.Default.Edit, "Edit ${search.name}") }
                    },
                    modifier = Modifier.clickable { viewModel.runSavedSearch(search) },
                )
            }
        }
    }
}
