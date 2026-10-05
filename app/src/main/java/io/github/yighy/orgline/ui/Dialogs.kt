package io.github.yighy.orgline.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsDialog(
    initial: List<String>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString(" ")) }
    val current = text.split(Regex("[\\s:]+")).filter { it.isNotBlank() }

    AlertDialog(
        // Closing the dialog keeps the tags typed
        onDismissRequest = { if (current != initial) onConfirm(current) else onDismiss() },
        title = { Text("Tags") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Space separated") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { tag ->
                            val on = current.any { it.equals(tag, ignoreCase = true) }
                            FilterChip(
                                selected = on,
                                onClick = {
                                    text = if (on) {
                                        current.filterNot { it.equals(tag, ignoreCase = true) }.joinToString(" ")
                                    } else {
                                        (current + tag).joinToString(" ")
                                    }
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PropertyDialog(
    name: String,
    value: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    var n by remember { mutableStateOf(name) }
    var v by remember { mutableStateOf(value) }
    AlertDialog(
        // Closing the dialog keeps what was typed
        onDismissRequest = {
            if (n.isNotBlank() && (n != name || v.trim() != value)) onConfirm(n, v.trim()) else onDismiss()
        },
        title = { Text(if (isNew) "New property" else "Property") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = n,
                    onValueChange = { n = it.replace(" ", "").uppercase() },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = v,
                    onValueChange = { v = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = n.isNotBlank(), onClick = { onConfirm(n, v.trim()) }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
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
        // Until changed here, the timestamp keeps its repeater as written (even one not offered)
        var repeat by remember { mutableStateOf(OrgTime.Repeat.parse(parts?.repeater)) }
        var repeatChanged by remember { mutableStateOf(false) }
        fun result(time: LocalTime?) =
            if (repeatChanged) OrgTime.build(date, time, initial, repeat?.toString()) else OrgTime.build(date, time, initial)
        val shownTime = { LocalTime.of(timeState.hour, timeState.minute) }
        AlertDialog(
            // Closing keeps the date with the time shown
            onDismissRequest = { onConfirm(result(shownTime())) },
            title = { Text("Time") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TimeInput(state = timeState)
                    RepeatPicker(repeat) { repeat = it; repeatChanged = true }
                }
            },
            confirmButton = { TextButton(onClick = { onConfirm(result(shownTime())) }) { Text("OK") } },
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
