package io.github.yighy.orgline.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.Agenda
import io.github.yighy.orgline.data.NoteRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgendaScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    snackbar: SnackbarHostState,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
) {
    val today = LocalDate.now()
    val agenda = remember(library, today) { Agenda.build(library.notes, today, 7) }
    val todayCount = agenda.overdue.size + agenda.days.first().entries.size
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Agenda") },
                subtitle = { Text(if (todayCount == 0) "Nothing for today" else "$todayCount for today") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = blendedTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PullToRefresh(viewModel) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    if (!notificationsAllowed) {
                        item(key = "notifications") { NotificationsCard(onAllowNotifications) }
                    }
                    agendaItems(viewModel, library, agenda, today)
                }
            }
        }
    }
}

/** Overdue entries, then each day with its entries (agenda screen, and searches with ad.). */
fun LazyListScope.agendaItems(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    agenda: Agenda.Result,
    today: LocalDate,
) {
    if (agenda.overdue.isNotEmpty()) {
        item(key = "overdue") { DayHeader("Overdue", MaterialTheme.colorScheme.error) }
        items(agenda.overdue, key = { "overdue-" + entryKey(it) }) { entry ->
            AgendaRow(viewModel, library, entry, today, overdue = true)
        }
    }
    agenda.days.forEach { day ->
        item(key = "day-${day.date}") { DayHeader(dayLabel(day.date, today), MaterialTheme.colorScheme.primary) }
        if (day.entries.isEmpty()) {
            item(key = "empty-${day.date}") {
                Text(
                    "Nothing planned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(day.entries, key = { "${day.date}-" + entryKey(it) }) { entry ->
            AgendaRow(viewModel, library, entry, today, overdue = false)
        }
    }
}

private fun entryKey(entry: Agenda.Entry) = "${entry.note.fileUri}#${entry.note.index}#${entry.kind}#${entry.time}"

@Composable
private fun DayHeader(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun weekday(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today · " + date.format(shortDate)
    today.plusDays(1) -> "Tomorrow · " + date.format(shortDate)
    else -> weekday(date) + " · " + date.format(shortDate)
}

/** Time, state and title of an agenda entry; swipe right toggles TODO / DONE. */
@Composable
private fun AgendaRow(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    entry: Agenda.Entry,
    today: LocalDate,
    overdue: Boolean,
) {
    val note = entry.note
    val key = (if (overdue) "agenda-overdue-" else "agenda-${entry.date}-") + entryKey(entry)
    SwipeRow(
        key = key,
        openKey = viewModel.openSwipeRow,
        onOpenChange = {},
        swipeRightIcon = if (note.isDone) OrglineIcons.Undo else OrglineIcons.Check,
        onSwipeRight = { viewModel.toggleDone(note) },
        actions = emptyList(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { viewModel.openNote(note) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start, and the end of a time range under it
            Column(Modifier.width(72.dp)) {
                Text(
                    entry.time?.format(timeFormat) ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.endTime?.let {
                    Text(it.format(timeFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    note.state?.let {
                        StateLabel(it, note.isDone)
                        Spacer(Modifier.width(8.dp))
                    }
                    note.priority?.let {
                        Text("#$it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                    }
                    LinkedText(
                        note.title,
                        MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                        ),
                        viewModel::openById,
                        maxLines = 2,
                    )
                }
                val deadline = entry.kind == Agenda.Kind.Deadline
                val details = buildList {
                    add(
                        when (entry.kind) {
                            Agenda.Kind.Scheduled -> "Scheduled"
                            Agenda.Kind.Deadline -> "Deadline"
                            Agenda.Kind.Event -> "Event"
                        },
                    )
                    if (overdue) {
                        val late = entry.daysLate(today)
                        add(if (late == 1L) "1 day late" else "$late days late")
                    }
                    add(library.file(note.fileUri)?.title ?: note.fileName)
                }
                Text(
                    details.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deadline || overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotificationsCard(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Allow notifications to get reminders at scheduled and deadline times.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllow) { Text("Allow") }
        }
    }
}
