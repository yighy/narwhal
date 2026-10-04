package io.github.yighy.orgline.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
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
import io.github.yighy.orgline.data.NoteRepository
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
            ListItem(
                headlineContent = { Text("Folder") },
                supportingContent = { Text(library.folderUri?.let(::folderLabel) ?: "None") },
                modifier = Modifier.clickable(onClick = onPickFolder),
            )
            SwitchItem(
                title = "Show note content in notebooks",
                summary = "Display each note's text under its title in the outline",
                checked = settings.showContentInNotebook,
                onCheckedChange = viewModel::setShowContentInNotebook,
            )

            ListItem(
                headlineContent = { Text("State keywords") },
                supportingContent = { Text(settings.keywords.toSpec() + "\nFor files without a #+TODO: line") },
                modifier = Modifier.clickable { editKeywords = true },
            )

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
                title = "For dates without a time",
                summary = "Notify at ${settings.allDayTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}",
                checked = settings.remindAllDay,
                onCheckedChange = viewModel::setRemindAllDay,
            )
            if (settings.remindAllDay) {
                ListItem(
                    headlineContent = { Text("Time for dates without a time") },
                    supportingContent = { Text(settings.allDayTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) },
                    modifier = Modifier.clickable { pickAllDayTime = true },
                )
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
        }
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
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun StatusItem(title: String, ok: Boolean, okText: String, problemText: String, onFix: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (ok) okText else problemText,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        modifier = if (ok) Modifier else Modifier.clickable(onClick = onFix),
    )
}

/** "primary:Documents/org" → "Documents/org". */
private fun folderLabel(treeUri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':') }
        .getOrNull()
        ?.ifEmpty { "/" }
        ?: treeUri.toString()
