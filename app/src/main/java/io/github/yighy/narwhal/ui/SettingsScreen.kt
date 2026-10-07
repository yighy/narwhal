package io.github.yighy.narwhal.ui

import io.github.yighy.narwhal.data.ThemeMode
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.yighy.narwhal.data.AppSettings
import io.github.yighy.narwhal.data.LinkRenaming
import io.github.yighy.narwhal.data.NoteSpacing
import io.github.yighy.narwhal.data.NoteRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    onPickFolder: () -> Unit,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var pickAllDayTime by remember { mutableStateOf(false) }
    var editKeywords by remember { mutableStateOf(false) }
    var pickLinkRenaming by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }
    var pickSnooze by remember { mutableStateOf(false) }
    var pickSpacing by remember { mutableStateOf(false) }
    var editHiddenProperties by remember { mutableStateOf(false) }

    // System settings can change while the app is in the background
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val unrestricted = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val exactAlarms = remember(resumes) { viewModel.canScheduleExactAlarms }

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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            ListItem(
                onClick = { pickTheme = true },
                supportingContent = { Text(settings.themeMode.label) },
            ) { Text("Theme") }
            // Wallpaper colors exist from Android 12
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchItem(
                    title = "Dynamic color",
                    summary = "Use the colors of your wallpaper",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SectionHeader("Notes")
            ListItem(
                onClick = onPickFolder,
                supportingContent = { Text(library.folderUri?.let(::folderLabel) ?: "None") },
            ) { Text("Folder") }
            SwitchItem(
                title = "Show note content in notebooks",
                summary = "Display each note's text under its title in the outline",
                checked = settings.showContentInNotebook,
                onCheckedChange = viewModel::setShowContentInNotebook,
            )
            ListItem(
                onClick = { pickSpacing = true },
                supportingContent = { Text(settings.noteSpacing.label) },
            ) { Text("Space between notes") }

            ListItem(
                onClick = { editKeywords = true },
                supportingContent = { Text(settings.keywords.toSpec() + "\nFor files without a #+TODO: line") },
            ) { Text("State keywords") }

            SwitchItem(
                title = "Add an ID to new notes",
                summary = "Fill the ID property when a note is created; a note also gets one when an id: link to it is made",
                checked = settings.addIdToNewNotes,
                onCheckedChange = viewModel::setAddIdToNewNotes,
            )
            ListItem(
                onClick = { editHiddenProperties = true },
                supportingContent = {
                    Text(settings.hiddenProperties.sorted().joinToString(" ").ifEmpty { "None" } + "\nNot shown on the note screen; kept in the file")
                },
            ) { Text("Hidden properties") }
            SwitchItem(
                title = "Add a creation date to new notes",
                summary = "Fill the CREATED property when a note is created (searched with cr., sorted with o.cr)",
                checked = settings.addCreatedToNewNotes,
                onCheckedChange = viewModel::setAddCreatedToNewNotes,
            )

            ListItem(
                onClick = { pickLinkRenaming = true },
                supportingContent = { Text(settings.linkRenaming.label + "\nLinks that still show the old title") },
            ) { Text("Links to a renamed note") }

            SectionHeader("Reminders")
            SwitchItem(
                title = "At scheduled times",
                summary = "Notify at the time of SCHEDULED",
                checked = settings.remindScheduled,
                onCheckedChange = viewModel::setRemindScheduled,
            )
            SwitchItem(
                title = "At deadline times",
                summary = "Notify at the time of DEADLINE",
                checked = settings.remindDeadline,
                onCheckedChange = viewModel::setRemindDeadline,
            )
            SwitchItem(
                title = "Use alarm clock alarms",
                summary = "Most reliable, even with battery saving; an alarm icon shows in the status bar until the next reminder",
                checked = settings.useAlarmClock,
                onCheckedChange = viewModel::setUseAlarmClock,
            )
            ListItem(
                onClick = { pickSnooze = true },
                supportingContent = { Text(AppSettings.snoozeLabel(settings.snoozeMinutes) + "\nThe notification's snooze button") },
            ) { Text("Snooze") }
            SwitchItem(
                title = "For dates without a time",
                summary = "Notify at ${settings.allDayTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}",
                checked = settings.remindAllDay,
                onCheckedChange = viewModel::setRemindAllDay,
            )
            if (settings.remindAllDay) {
                ListItem(
                    onClick = { pickAllDayTime = true },
                    supportingContent = { Text(settings.allDayTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) },
                ) { Text("Time for dates without a time") }
            }

            SectionHeader("Reliability")
            StatusItem(
                title = "Notifications",
                ok = notificationsAllowed,
                okText = "Allowed",
                problemText = "Off: reminders can't be shown. Tap to allow.",
                onFix = onAllowNotifications,
            )
            StatusItem(
                title = "Exact alarms",
                ok = exactAlarms,
                okText = "Allowed: reminders ring on time",
                problemText = "Off: reminders may be late. Tap to allow.",
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    }
                },
            )
            StatusItem(
                title = "Battery optimization",
                ok = unrestricted,
                okText = "Not restricted",
                problemText = "Restricted: the system may delay reminders. Tap to remove the restriction.",
                onFix = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )

            SectionHeader("About")
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
            ListItem(
                supportingContent = { Text("Version ${version ?: "?"}") },
            ) { Text("Narwhal") }
            fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            ListItem(
                onClick = { open(SOURCE_URL) },
                supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
            ) { Text("Source code") }
            ListItem(
                onClick = { open(LICENSE_URL) },
                supportingContent = { Text("GNU General Public License v3") },
            ) { Text("License") }
            ListItem(
                onClick = { open(ORG_JAVA_URL) },
                supportingContent = { Text("Inspired by Orgzly; Org files read and written with its org-java library") },
            ) { Text("Credits") }
        }
    }

    if (pickTheme) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.label },
            selected = settings.themeMode.label,
            onDismiss = { pickTheme = false },
            onSelect = { label ->
                pickTheme = false
                ThemeMode.entries.firstOrNull { it.label == label }?.let(viewModel::setThemeMode)
            },
        )
    }
    if (editHiddenProperties) {
        TextInputDialog(
            title = "Hidden properties",
            label = "Names, separated by spaces",
            confirmLabel = "Save",
            initial = settings.hiddenProperties.sorted().joinToString(" "),
            allowEmpty = true,
            onDismiss = { editHiddenProperties = false },
            onConfirm = { text ->
                editHiddenProperties = false
                viewModel.setHiddenProperties(AppSettings.parseProperties(text))
            },
        )
    }
    if (pickSpacing) {
        ChoiceDialog(
            title = "Space between notes",
            options = NoteSpacing.entries.map { it.label },
            selected = settings.noteSpacing.label,
            onDismiss = { pickSpacing = false },
            onSelect = { label ->
                pickSpacing = false
                NoteSpacing.entries.firstOrNull { it.label == label }?.let(viewModel::setNoteSpacing)
            },
        )
    }
    if (pickSnooze) {
        ChoiceDialog(
            title = "Snooze",
            options = AppSettings.SNOOZE_CHOICES.map(AppSettings::snoozeLabel),
            selected = AppSettings.snoozeLabel(settings.snoozeMinutes),
            onDismiss = { pickSnooze = false },
            onSelect = { label ->
                pickSnooze = false
                AppSettings.SNOOZE_CHOICES.firstOrNull { AppSettings.snoozeLabel(it) == label }?.let(viewModel::setSnoozeMinutes)
            },
        )
    }
    if (pickLinkRenaming) {
        ChoiceDialog(
            title = "Links to a renamed note",
            options = LinkRenaming.entries.map { it.label },
            selected = settings.linkRenaming.label,
            onDismiss = { pickLinkRenaming = false },
            onSelect = { label ->
                pickLinkRenaming = false
                LinkRenaming.entries.firstOrNull { it.label == label }?.let(viewModel::setLinkRenaming)
            },
        )
    }
    if (editKeywords) {
        TextInputDialog(
            title = "State keywords",
            label = "To-do keywords | done keywords",
            confirmLabel = "Save",
            initial = settings.keywords.toSpec(),
            onDismiss = { editKeywords = false },
            onConfirm = { spec -> if (viewModel.setKeywords(spec)) editKeywords = false else viewModel.message("Type at least one keyword") },
        )
    }

    if (pickAllDayTime) {
        val state = rememberTimePickerState(
            initialHour = settings.allDayTime.hour,
            initialMinute = settings.allDayTime.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickAllDayTime = false },
            title = { Text("Time for dates without a time") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    pickAllDayTime = false
                    viewModel.setAllDayTime(LocalTime.of(state.hour, state.minute))
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickAllDayTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        onClick = { onCheckedChange(!checked) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    ) { Text(title) }
}

@Composable
private fun StatusItem(title: String, ok: Boolean, okText: String, problemText: String, onFix: () -> Unit) {
    val status: @Composable () -> Unit = {
        Text(
            if (ok) okText else problemText,
            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
    // A tap fixes it, only when there is something to fix
    if (ok) {
        ListItem(supportingContent = status) { Text(title) }
    } else {
        ListItem(onClick = onFix, supportingContent = status) { Text(title) }
    }
}

private const val SOURCE_URL = "https://github.com/yighy/narwhal"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val ORG_JAVA_URL = "https://github.com/orgzly/org-java"

/** "primary:Documents/org" → "Documents/org". */
private fun folderLabel(treeUri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':') }
        .getOrNull()
        ?.ifEmpty { "/" }
        ?: treeUri.toString()
