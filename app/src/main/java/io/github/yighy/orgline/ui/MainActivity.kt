package io.github.yighy.orgline.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.yighy.orgline.reminders.ReminderNotifications
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

    /** Refreshed on resume, since the user can change it in system settings. */
    private var notificationsAllowed by mutableStateOf(false)

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = ReminderNotifications.canPost(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        setContent {
            OrglineTheme {
                OrglineApp(viewModel, notificationsAllowed, ::askForNotifications)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed = ReminderNotifications.canPost(this)
        // Pick up changes made by other apps (Syncthing, editors…)
        viewModel.refresh()
    }

    /** Asks for the permission the first time, then opens the system settings (the dialog won't show again). */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (!viewModel.askedNotificationPermission || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            viewModel.askedNotificationPermission = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val uri = intent?.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
        viewModel.openFromNotification(
            uri,
            intent.getIntExtra(ReminderNotifications.EXTRA_INDEX, -1),
            intent.getStringExtra(ReminderNotifications.EXTRA_TITLE),
        )
        intent.removeExtra(ReminderNotifications.EXTRA_FILE_URI)
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
fun OrglineApp(viewModel: AppViewModel, notificationsAllowed: Boolean, onAllowNotifications: () -> Unit) {
    val library by viewModel.library.collectAsState()

    // A note requested from a notification opens once the notes are loaded
    LaunchedEffect(library.loaded) { viewModel.openPendingNote() }

    // Ask once for notifications, so reminders work out of the box
    LaunchedEffect(library.loaded) {
        if (library.loaded && !notificationsAllowed && !viewModel.askedNotificationPermission) onAllowNotifications()
    }
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
                    selected = top != Screen.Agenda,
                    onClick = { close(viewModel::openHome) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Agenda") },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    selected = top == Screen.Agenda,
                    onClick = { close(viewModel::openAgenda) },
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
                            screen, viewModel, library, snackbar, notificationsAllowed, onAllowNotifications,
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
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPickFolder: () -> Unit,
) {
    when (screen) {
        Screen.Agenda -> AgendaScreen(viewModel, library, snackbar, notificationsAllowed, onAllowNotifications)

        Screen.Notebooks -> HomeScreen(viewModel, library, snackbar, onOpenDrawer)

        Screen.Settings -> SettingsScreen(viewModel, library, onPickFolder, notificationsAllowed, onAllowNotifications)

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
