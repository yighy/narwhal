package io.github.yighy.orgline.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.yighy.orgline.data.NoteRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, library: NoteRepository.Library, onPickFolder: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = blendedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Folder") },
                supportingContent = { Text(library.folderUri?.let(::folderLabel) ?: "None") },
                modifier = Modifier.clickable(onClick = onPickFolder),
            )
            ListItem(
                headlineContent = { Text("Show note content in notebooks") },
                supportingContent = { Text("Display each note's text under its title in the outline") },
                trailingContent = {
                    Switch(checked = settings.showContentInNotebook, onCheckedChange = viewModel::setShowContentInNotebook)
                },
                modifier = Modifier.clickable {
                    viewModel.setShowContentInNotebook(!settings.showContentInNotebook)
                },
            )
        }
    }
}

/** "primary:Documents/org" → "Documents/org". */
private fun folderLabel(treeUri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':') }
        .getOrNull()
        ?.ifEmpty { "/" }
        ?: treeUri.toString()
