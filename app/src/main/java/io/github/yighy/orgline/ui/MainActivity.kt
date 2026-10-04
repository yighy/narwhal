package io.github.yighy.orgline.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrglineTheme {
                OrglineApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up changes made by other apps (Syncthing, editors…)
        viewModel.refresh()
    }
}

@Composable
fun OrglineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun OrglineApp(viewModel: AppViewModel) {
    val library by viewModel.library.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setFolder(uri)
    }

    BackHandler(enabled = viewModel.stack.size > 1) { viewModel.back() }

    when (val screen = viewModel.stack.last()) {
        Screen.Notes ->
            if (library.folderUri == null) {
                WelcomeScreen(onPickFolder = { pickFolder.launch(null) })
            } else {
                NoteListScreen(viewModel, library, snackbar, onPickFolder = { pickFolder.launch(null) })
            }

        is Screen.Note -> {
            val note = library.note(screen.fileUri, screen.index)
            if (note == null) {
                // Note disappeared (file changed elsewhere)
                LaunchedEffect(screen) { viewModel.back() }
            } else {
                NoteScreen(viewModel, library, note, screen.editing, snackbar)
            }
        }
    }
}
