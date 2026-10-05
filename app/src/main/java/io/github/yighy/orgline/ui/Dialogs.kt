package io.github.yighy.orgline.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import java.text.BreakIterator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.OrgTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    suffix: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                suffix = suffix?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Single choice among [options]; null is shown as "None". */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<String?>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(option ?: "None", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Picks a date, then an optional time and how it repeats. Calls [onConfirm] with an Org
 * timestamp, or null when the date is removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimestampDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val parts = remember(initial) { OrgTime.parts(initial) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (parts?.date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val initialDate = parts?.date ?: LocalDate.now()
        fun picked() = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        DatePickerDialog(
            // Closing the calendar keeps a date that was changed (with the time it had)
            onDismissRequest = {
                val date = picked()
                if (date != null && (initial == null || date != initialDate)) {
                    onConfirm(OrgTime.build(date, parts?.time, initial))
                } else {
                    onDismiss()
                }
            },
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        pickedDate = Instant.ofEpochMilli(dateState.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    },
                ) { Text("Next") }
            },
            dismissButton = {
                Row {
                    if (initial != null) TextButton(onClick = { onConfirm(null) }) { Text("Remove") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val start = parts?.time ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        // Optional end of a time range ("09:00-10:30"), an hour after the start at first
        var hasEnd by remember { mutableStateOf(parts?.endTime != null) }
        val end = parts?.endTime ?: start.plusHours(1)
        val endState = rememberTimePickerState(
            initialHour = end.hour,
            initialMinute = end.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        // Until changed here, the timestamp keeps its repeater as written (even one not offered)
        var repeat by remember { mutableStateOf(OrgTime.Repeat.parse(parts?.repeater)) }
        var repeatChanged by remember { mutableStateOf(false) }
        fun result(time: LocalTime?): String {
            val endTime = LocalTime.of(endState.hour, endState.minute).takeIf { hasEnd && time != null }
            return if (repeatChanged) {
                OrgTime.build(date, time, initial, repeat?.toString(), endTime)
            } else {
                OrgTime.build(date, time, initial, endTime = endTime)
            }
        }
        val shownTime = { LocalTime.of(timeState.hour, timeState.minute) }
        // An end time must come after the start
        val endTooEarly = hasEnd && !LocalTime.of(endState.hour, endState.minute).isAfter(shownTime())
        AlertDialog(
            // Closing keeps the date with the time shown (not with an end before the start)
            onDismissRequest = { if (endTooEarly) onDismiss() else onConfirm(result(shownTime())) },
            title = { Text("Time") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TimeInput(state = timeState)
                    // End time, as in Orgzly
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.toggleable(value = hasEnd, onValueChange = { hasEnd = it }),
                    ) {
                        Text("End time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Switch(checked = hasEnd, onCheckedChange = null)
                    }
                    AnimatedVisibility(hasEnd) {
                        Column {
                            TimeInput(state = endState, modifier = Modifier.padding(top = 8.dp))
                            if (endTooEarly) {
                                Text(
                                    "The end time must be after the start.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    RepeatPicker(repeat) { repeat = it; repeatChanged = true }
                }
            },
            confirmButton = { TextButton(enabled = !endTooEarly, onClick = { onConfirm(result(shownTime())) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { onConfirm(result(null)) }) { Text("No time") } },
        )
    }
}

/** How a date repeats: interval, and how the next date is found once done (Org's +, ++ and .+). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPicker(repeat: OrgTime.Repeat?, onChange: (OrgTime.Repeat?) -> Unit) {
    val units = buildList {
        if (repeat?.unit == 'h') add('h' to "Hourly")
        add('d' to "Daily")
        add('w' to "Weekly")
        add('m' to "Monthly")
        add('y' to "Yearly")
    }
    Text("Repeat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = repeat == null, onClick = { onChange(null) }, label = { Text("Never") })
        units.forEach { (unit, label) ->
            FilterChip(
                selected = repeat?.unit == unit,
                onClick = { onChange(OrgTime.Repeat(repeat?.type ?: "+", repeat?.every ?: 1, unit)) },
                label = { Text(label) },
            )
        }
    }
    if (repeat == null) return

    var every by remember { mutableStateOf(repeat.every.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Every", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = every,
            onValueChange = { text ->
                every = text.filter { it.isDigit() }.take(3)
                every.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(repeat.copy(every = it, rest = "")) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(12.dp))
        val names = mapOf('h' to "hour", 'd' to "day", 'w' to "week", 'm' to "month", 'y' to "year")
        Text(names.getValue(repeat.unit) + if (repeat.every == 1) "" else "s", style = MaterialTheme.typography.bodyLarge)
    }

    Text("Once done", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    listOf(
        "+" to "Move by the interval",
        "++" to "Move to the first date after today",
        ".+" to "Start again from the day it is done",
    ).forEach { (type, label) ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = repeat.type == type, onClick = { onChange(repeat.copy(type = type, rest = "")) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = repeat.type == type, onClick = { onChange(repeat.copy(type = type, rest = "")) })
            // The symbol as written in the file, for those who know Org
            Text(
                type,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .width(32.dp)
                    .padding(vertical = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

private val ICON_SUGGESTIONS = listOf(
    "📓", "📚", "📝", "✅", "📌", "⭐", "💡", "🎯",
    "🏠", "🛒", "🍳", "🌱", "🐾", "🚗", "✈️", "🏖️",
    "💼", "💻", "🛠️", "📈", "💰", "📅", "🎓", "🧪",
    "❤️", "🏃", "🧘", "🎵", "🎮", "🎨", "📷", "🎁",
)

/** First character as seen on screen (an emoji can be several code points). */
private fun firstGrapheme(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val it = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
    return trimmed.substring(0, it.next())
}

/** Picks an emoji as a notebook's icon; "Use initial" goes back to its first letter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconDialog(current: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notebook icon") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow {
                    ICON_SUGGESTIONS.forEach { emoji ->
                        val chosen = emoji == current
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { onSelect(emoji) },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Any other emoji, from the keyboard
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Other emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = firstGrapheme(typed) != null, onClick = { onSelect(firstGrapheme(typed)) }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSelect(null) }) { Text("Use initial") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Name and query of a saved search; [onDelete] is offered for one already saved. */
@Composable
fun SavedSearchDialog(
    name: String,
    query: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var n by remember { mutableStateOf(name) }
    var q by remember { mutableStateOf(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "Save search" else "Saved search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = n,
                    onValueChange = { n = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Query") },
                    supportingText = { Text("Like  i.todo t.work s.today  o.p") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = n.isNotBlank() && q.isNotBlank(), onClick = { onSave(n, q) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
