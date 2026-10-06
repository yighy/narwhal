package io.github.yighy.narwhal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.yighy.narwhal.core.OrgTime
import io.github.yighy.narwhal.core.TextEditing
import java.time.LocalDate

/** Undo and redo of a text being edited; typing a word in a row counts as one step. */
class EditHistory {
    private val undos = ArrayDeque<TextFieldValue>()
    private val redos = ArrayDeque<TextFieldValue>()
    private var lastTyping = 0L

    /** Bumped on each change, so buttons follow [canUndo] and [canRedo]. */
    private var version by mutableIntStateOf(0)

    val canUndo: Boolean get() = version >= 0 && undos.isNotEmpty()
    val canRedo: Boolean get() = version >= 0 && redos.isNotEmpty()

    /** Keeps [before] as a step, unless [typing] goes on from the last one. */
    fun record(before: TextFieldValue, typing: Boolean) {
        val now = System.currentTimeMillis()
        if (!typing || now - lastTyping > TYPING_STEP_MS || undos.isEmpty()) {
            undos.addLast(before)
            if (undos.size > MAX_STEPS) undos.removeFirst()
        }
        lastTyping = if (typing) now else 0L
        redos.clear()
        version++
    }

    fun undo(current: TextFieldValue): TextFieldValue? = undos.removeLastOrNull()?.also {
        redos.addLast(current)
        lastTyping = 0L
        version++
    }

    fun redo(current: TextFieldValue): TextFieldValue? = redos.removeLastOrNull()?.also {
        undos.addLast(current)
        lastTyping = 0L
        version++
    }

    // Text as typed before the last help, and as the help made it
    private var helpedFrom: String? = null
    private var helpedTo: TextFieldValue? = null

    fun adjusted(from: String, to: TextFieldValue) {
        helpedFrom = from
        helpedTo = to
    }

    /**
     * Right after a help, the keyboard can send back the text as it last knew it (without the
     * help): the help is kept. Returns what to show instead of [new], or null.
     */
    fun reverted(new: TextFieldValue): TextFieldValue? {
        val from = helpedFrom
        val to = helpedTo
        helpedFrom = null
        helpedTo = null
        return to?.takeIf { new.text == from }
    }

    fun clear() {
        undos.clear()
        redos.clear()
        version++
    }

    private companion object {
        const val TYPING_STEP_MS = 1000L
        const val MAX_STEPS = 100
    }
}

private fun TextFieldValue.toEdit() = TextEditing.Edit(text, selection.min, selection.max)

private fun TextEditing.Edit.toValue() = TextFieldValue(text, TextRange(start, end))

/**
 * What typing in a note's text gives, with its help (lists that go on with [lists], brackets
 * that close); [history] keeps the step.
 */
fun typedText(old: TextFieldValue, new: TextFieldValue, history: EditHistory, lists: Boolean = true): TextFieldValue {
    if (new.text == old.text) return new
    history.reverted(new)?.let { return it }
    // A space or a new line ends a step of typing: undo goes back word by word
    val typedSpace = new.text.length == old.text.length + 1 && new.text.getOrNull(old.selection.min)?.isWhitespace() == true
    history.record(old, typing = !typedSpace)
    val adjusted = TextEditing.afterTyping(old.toEdit(), new.toEdit(), lists)?.toValue() ?: return new
    history.adjusted(new.text, adjusted)
    return adjusted
}

/**
 * Bar over the keyboard while a note's text is edited: bold, italic, underline, strike-through,
 * code, list, checkbox, indent, outdent, link (id: then its suggestions), today's date; undo and
 * redo stay at the end.
 */
@Composable
fun EditToolbar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, history: EditHistory) {
    fun apply(edit: (TextEditing.Edit) -> TextEditing.Edit) {
        val changed = edit(value.toEdit()).toValue()
        if (changed.text == value.text && changed.selection == value.selection) return
        history.record(value, typing = false)
        onChange(changed)
    }
    /** Inserts [text] with a space before it when it would stick to a word. */
    fun insertWord(text: String) = apply { e ->
        val before = e.text.getOrNull(e.start - 1)
        TextEditing.insert(e, if (before == null || before.isWhitespace()) text else " $text")
    }
    fun emphasis(marker: Char) = apply { TextEditing.toggleEmphasis(it, marker) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        // Buttons don't take the focus: the text keeps it, and the keyboard stays
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { emphasis('*') }) { Icon(NarwhalIcons.Bold, "Bold") }
                IconButton(onClick = { emphasis('/') }) { Icon(NarwhalIcons.Italic, "Italic") }
                IconButton(onClick = { emphasis('_') }) { Icon(NarwhalIcons.Underline, "Underline") }
                IconButton(onClick = { emphasis('+') }) { Icon(NarwhalIcons.Strike, "Strike-through") }
                IconButton(onClick = { emphasis('~') }) { Icon(NarwhalIcons.Code, "Code") }
                IconButton(onClick = { apply(TextEditing::toggleList) }) { Icon(NarwhalIcons.ListBulleted, "List") }
                IconButton(onClick = { apply(TextEditing::toggleCheckbox) }) { Icon(NarwhalIcons.CheckBox, "Checkbox") }
                IconButton(onClick = { apply(TextEditing::indent) }) { Icon(NarwhalIcons.Indent, "Indent") }
                IconButton(onClick = { apply(TextEditing::outdent) }) { Icon(NarwhalIcons.Outdent, "Outdent") }
                IconButton(onClick = { insertWord("id:") }) { Icon(NarwhalIcons.Link, "Link to a note") }
                IconButton(onClick = { insertWord(OrgTime.build(LocalDate.now(), null)) }) { Icon(Icons.Default.DateRange, "Today's date") }
            }
            VerticalDivider(Modifier.height(24.dp))
            IconButton(enabled = history.canUndo, onClick = { history.undo(value)?.let(onChange) }) { Icon(NarwhalIcons.Undo, "Undo") }
            IconButton(enabled = history.canRedo, onClick = { history.redo(value)?.let(onChange) }) { Icon(NarwhalIcons.Redo, "Redo") }
        }
    }
}
