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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.data.NoteRepository
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OrglineApp(viewModel: AppViewModel) {
    val library by viewModel.library.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setFolder(uri)
    }

    BackHandler(enabled = viewModel.stack.size > 1) { viewModel.back() }

    if (library.folderUri == null) {
        WelcomeScreen(onPickFolder = { pickFolder.launch(null) })
        return
    }

    val top = viewModel.stack.last()

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = top == Screen.Notebooks,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Orgline",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
                )
                fun close(action: () -> Unit) {
                    scope.launch { drawer.close() }
                    action()
                }
                NavigationDrawerItem(
                    label = { Text("Notebooks") },
                    icon = { Icon(Icons.Default.Home, null) },
                    selected = true,
                    onClick = { close(viewModel::openHome) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Agenda") },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    badge = { Text("Soon") },
                    selected = false,
                    onClick = { close { viewModel.message("The agenda is coming in the next version") } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, null) },
                    selected = false,
                    onClick = { close(viewModel::openSettings) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        val motion = MaterialTheme.motionScheme
        // App background behind the screens while they cross-fade
        SharedTransitionLayout(Modifier.background(MaterialTheme.colorScheme.surface)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                AnimatedContent(
                    targetState = top,
                    transitionSpec = {
                        val fade = motion.defaultEffectsSpec<Float>()
                        if (isContainerTransform(initialState, targetState)) {
                            // The card or row grows into the next screen (shared bounds); just cross-fade the rest
                            fadeIn(fade) togetherWith fadeOut(fade)
                        } else {
                            val slide = motion.defaultSpatialSpec<IntOffset>()
                            val dir = if (viewModel.navigatedForward) 1 else -1
                            (slideInHorizontally(slide) { dir * it / 3 } + fadeIn(fade)) togetherWith
                                (slideOutHorizontally(slide) { -dir * it / 3 } + fadeOut(fade))
                        }
                    },
                    label = "screens",
                ) { screen ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        ScreenContent(
                            screen, viewModel, library, snackbar,
                            onOpenDrawer = { scope.launch { drawer.open() } },
                            onPickFolder = { pickFolder.launch(null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    snackbar: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onPickFolder: () -> Unit,
) {
    when (screen) {
        Screen.Notebooks -> HomeScreen(viewModel, library, snackbar, onOpenDrawer)

        Screen.Settings -> SettingsScreen(viewModel, library, onPickFolder)

        is Screen.Notebook -> {
            val file = library.file(screen.fileUri)
            if (file != null) {
                NotebookScreen(viewModel, library, file, snackbar)
            } else if (!library.loading) {
                LaunchedEffect(screen) { viewModel.back() }
            }
        }

        is Screen.Note -> {
            val note = library.note(screen.fileUri, screen.index)
            if (note != null) {
                NoteScreen(viewModel, library, note, snackbar)
            } else if (!library.loading) {
                // Note disappeared (file changed elsewhere)
                LaunchedEffect(screen) { viewModel.back() }
            }
        }
    }
}

/** Notebook card ↔ notebook, and outline row ↔ note, share their container. */
private fun isContainerTransform(a: Screen, b: Screen): Boolean {
    fun pair(x: Screen, y: Screen) =
        (x == Screen.Notebooks && y is Screen.Notebook) ||
            (x is Screen.Notebook && y is Screen.Note && x.fileUri == y.fileUri)
    return pair(a, b) || pair(b, a)
}
