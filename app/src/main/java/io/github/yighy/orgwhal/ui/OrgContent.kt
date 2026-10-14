package io.github.yighy.orgwhal.ui

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.TextButton
import io.github.yighy.orgwhal.core.TableFormulas
import androidx.compose.ui.res.stringResource
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.ListFolds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.Blocks
import io.github.yighy.orgwhal.core.Checkboxes

/**
 * A note's content as Org shows it: text with its links and checkboxes ([LinkedText]), tables,
 * source and example blocks in a monospace box, quotes with a bar; drawers folded under their
 * name, the logbook hidden unless [showLogbook].
 * Taps give positions in [text], as for [LinkedText].
 */
@Composable
fun OrgContent(
    text: String,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTextTap: ((Int) -> Unit)? = null,
    onToggleCheckbox: ((Int) -> Unit)? = null,
    /** Given, tables with formulas offer to recalculate: the new text (null if unchanged), formulas skipped. */
    onRecalculate: ((String?, Int) -> Unit)? = null,
    /** Lines of the table with this name (#+NAME, or a note's ID), for remote() in formulas. */
    tableNamed: (String) -> List<String>? = { null },
    /** Tables with formulas show their values recalculated (the file keeps its own until saved). */
    liveFormulas: Boolean = false,
    /** Given, a tap on a table field opens it for editing (not those a formula computes). */
    onEditCell: ((Blocks.CellEdit) -> Unit)? = null,
    /** The :LOGBOOK: drawer folds as the others, for its clock lines; else it is hidden. */
    showLogbook: Boolean = false,
) {
    val blocks = remember(text) { Blocks.parse(text) }
    // Plain text, the usual case, shows as written
    val plain = blocks.size == 1 && blocks[0] is Blocks.Text
    if (plain && !remember(text) { ListFolds.hasFolds(text) }) {
        LinkedText(text, style, onOpenLink, modifier, onTextTap = onTextTap, onToggleCheckbox = onToggleCheckbox)
        return
    }
    val boxes = remember(text) { Checkboxes.find(text) }
    // Positions and checkbox numbers within a part are from its start
    fun tapFrom(start: Int) = onTextTap?.let { tap -> { at: Int -> tap(start + at) } }
    fun toggleFrom(start: Int): ((Int) -> Unit)? {
        val before = boxes.count { it.box < start }
        return onToggleCheckbox?.let { toggle -> { n: Int -> toggle(before + n) } }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                // Drawers open with a tap; the logbook only where asked, the clock's time
                // showing it elsewhere
                is Blocks.Drawer -> if (showLogbook || !block.name.equals("LOGBOOK", ignoreCase = true)) {
                    DrawerBlock(block, style, onOpenLink, tapFrom(block.innerStart), toggleFrom(block.innerStart))
                }
                is Blocks.Text -> FoldableText(block.text, block.range.first, style, onOpenLink, ::tapFrom, ::toggleFrom)
                is Blocks.Table -> Column {
                    block.name?.let { ElementName(it) }
                    // Recalculated, the table shows its new values; the button then saves them
                    val fresh = if (liveFormulas && block.formulas.isNotEmpty()) {
                        remember(text, block, tableNamed) {
                            val lines = text.substring(block.tableStart, block.tableEnd).split('\n')
                            val result = TableFormulas.recalculate(lines, block.formulas, tableNamed).lines
                            if (result == lines) null else Blocks.parse(result.joinToString("\n")).filterIsInstance<Blocks.Table>().firstOrNull()
                        }
                    } else {
                        null
                    }
                    // Fields as the file has them, those a formula computes left out
                    val tableIndex = blocks.filterIsInstance<Blocks.Table>().indexOfFirst { it === block }
                    val computed = remember(text, block) {
                        TableFormulas.computedCells(text.substring(block.tableStart, block.tableEnd).split('\n'), block.formulas)
                    }
                    // The field being edited in place, and its text
                    var editing by remember(text) { mutableStateOf<Pair<Int, Int>?>(null) }
                    var draft by remember(text) { mutableStateOf(TextFieldValue()) }
                    fun stored(line: Int, column: Int) = block.rows.getOrNull(line)?.getOrNull(column).orEmpty()
                    /** Saves the field being edited, if it changed. */
                    fun commit() {
                        val (line, column) = editing ?: return
                        editing = null
                        if (draft.text.trim() != stored(line, column)) onEditCell?.invoke(Blocks.CellEdit(tableIndex, line, column, draft.text))
                    }
                    OrgTable(
                        fresh?.let { block.copy(rows = it.rows) } ?: block,
                        style,
                        onOpenLink,
                        Modifier.tapToEdit(onTextTap, block.range.first),
                        onCellTap = onEditCell?.let {
                            { line, column ->
                                if ((line to column) in computed || editing == line to column) {
                                    null
                                } else {
                                    {
                                        commit()
                                        val value = stored(line, column)
                                        draft = TextFieldValue(value, TextRange(0, value.length))
                                        editing = line to column
                                    }
                                }
                            }
                        },
                        editing = editing,
                        editor = { line, column, cellStyle ->
                            // Kept in view above the keyboard as it comes up, with the field's
                            // padding and border under it
                            val bring = remember { BringIntoViewRequester() }
                            val density = LocalDensity.current
                            val ime = WindowInsets.ime
                            var size by remember { mutableStateOf(IntSize.Zero) }
                            LaunchedEffect(Unit) {
                                val margin = with(density) { 16.dp.toPx() }
                                snapshotFlow { ime.getBottom(density) }.collect {
                                    bring.bringIntoView(Rect(0f, -margin, size.width.toFloat(), size.height + margin))
                                }
                            }
                            InPlaceField(
                                value = draft,
                                // Only while this field is the one edited: losing the focus, it
                                // sends its text again, which would go into the next field
                                onValueChange = { if (editing == line to column) draft = it },
                                style = cellStyle,
                                onFocusLost = { if (editing == line to column) commit() },
                                onDone = ::commit,
                                keyboardType = if (TableFormulas.isNumber(stored(line, column))) KeyboardType.Decimal else KeyboardType.Text,
                                // A field is one line: Enter saves it
                                singleLine = true,
                                modifier = Modifier.width(IntrinsicSize.Min).bringIntoViewRequester(bring).onSizeChanged { size = it },
                            )
                        },
                    )
                    if (block.formulaLines.isNotEmpty()) {
                        FormulaBar(block, stale = fresh != null, onRecalculate?.let { done ->
                            {
                                val lines = text.substring(block.tableStart, block.tableEnd).split('\n')
                                val result = TableFormulas.recalculate(lines, block.formulas, tableNamed)
                                val changed = result.lines != lines
                                done(if (changed) text.replaceRange(block.tableStart, block.tableEnd, result.lines.joinToString("\n")) else null, result.skipped)
                            }
                        })
                    }
                }
                is Blocks.Begin -> when {
                    block.isCode -> Column {
                        block.name?.let { ElementName(it) }
                        CodeBlock(block, style, Modifier.tapToEdit(onTextTap, block.innerStart))
                    }
                    block.kind == "QUOTE" -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        OrgContent(
                            block.inner,
                            style,
                            onOpenLink,
                            Modifier.weight(1f),
                            onTextTap = tapFrom(block.innerStart),
                            onToggleCheckbox = toggleFrom(block.innerStart),
                        )
                    }
                    // Verse keeps its lines as written; centered text is centered
                    else -> LinkedText(
                        block.inner,
                        if (block.kind == "CENTER") style.copy(textAlign = TextAlign.Center) else style,
                        onOpenLink,
                        Modifier.fillMaxWidth(),
                        onTextTap = tapFrom(block.innerStart),
                        onToggleCheckbox = toggleFrom(block.innerStart),
                    )
                }
            }
        }
    }
}

/**
 * Text whose list items with sub-items fold, with a chevron at the end of their line. [start] is
 * where the text is in the content; [tapFrom] and [toggleFrom] give the callbacks for a part
 * starting at a position in the content.
 */
@Composable
private fun FoldableText(
    text: String,
    start: Int,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    tapFrom: (Int) -> ((Int) -> Unit)?,
    toggleFrom: (Int) -> ((Int) -> Unit)?,
) {
    val parts = remember(text) { ListFolds.parse(text) }
    if (parts.none { it is ListFolds.Item }) {
        LinkedText(text, style, onOpenLink, onTextTap = tapFrom(start), onToggleCheckbox = toggleFrom(start))
        return
    }
    Column {
        parts.forEach { part ->
            when (part) {
                is ListFolds.Plain -> LinkedText(
                    part.text,
                    style,
                    onOpenLink,
                    onTextTap = tapFrom(start + part.start),
                    onToggleCheckbox = toggleFrom(start + part.start),
                )
                is ListFolds.Item -> {
                    var open by rememberSaveable(start + part.start) { mutableStateOf(true) }
                    val rotation = chevronRotation(open)
                    Row(verticalAlignment = Alignment.Top) {
                        LinkedText(
                            part.line,
                            style,
                            onOpenLink,
                            Modifier.weight(1f),
                            onTextTap = tapFrom(start + part.start),
                            onToggleCheckbox = toggleFrom(start + part.start),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(if (open) R.string.collapse else R.string.expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { open = !open }
                                .padding(horizontal = 4.dp)
                                .size(20.dp)
                                .rotate(rotation),
                        )
                    }
                    Unfold(open) {
                        FoldableText(part.children, start + part.childrenStart, style, onOpenLink, tapFrom, toggleFrom)
                    }
                }
            }
        }
    }
}

/** A table's formulas, folded to "ƒ 2 formulas", with a button to recalculate the table. */
@Composable
private fun FormulaBar(table: Blocks.Table, stale: Boolean, onRecalculate: (() -> Unit)?) {
    var open by rememberSaveable(table.range.first) { mutableStateOf(false) }
    val rotation = chevronRotation(open)
    val count = table.formulas.size
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { open = !open }
                    .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(rotation),
                )
                Text(
                    "ƒ  " + pluralStringResource(R.plurals.n_formulas, count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (onRecalculate != null) {
                TextButton(onClick = onRecalculate) { Text(stringResource(if (stale) R.string.save_values else R.string.recalculate)) }
            }
        }
        Unfold(open) {
            Text(
                table.formulas.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()),
                softWrap = false,
            )
        }
    }
}

/** A drawer folded to its name, as in Emacs; a tap shows or hides its text. */
@Composable
private fun DrawerBlock(
    drawer: Blocks.Drawer,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    onTextTap: ((Int) -> Unit)?,
    onToggleCheckbox: ((Int) -> Unit)?,
) {
    var open by rememberSaveable(drawer.name, drawer.innerStart) { mutableStateOf(false) }
    val rotation = chevronRotation(open)
    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
            Text(drawer.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Unfold(open) {
            LinkedText(
                drawer.inner,
                style,
                onOpenLink,
                Modifier.padding(start = 20.dp, top = 4.dp),
                onTextTap = onTextTap,
                onToggleCheckbox = onToggleCheckbox,
            )
        }
    }
}

/** The name given to a table or block with #+NAME, which links and remote() refer to. */
@Composable
private fun ElementName(name: String) {
    Text(
        "#$name",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Rotation of a fold chevron: pointing down when [open], right when folded. */
@Composable
private fun chevronRotation(open: Boolean): Float =
    animateFloatAsState(if (open) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron").value

/** [content] unfolding and folding away as [open] changes. */
@Composable
private fun Unfold(open: Boolean, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        open,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) { content() }
}

/** A tap edits the text from [at] (no ripple, as on the text around). */
private fun Modifier.tapToEdit(onTextTap: ((Int) -> Unit)?, at: Int) =
    if (onTextTap == null) this else clickable(interactionSource = null, indication = null) { onTextTap(at) }

/** Code in a monospace box that scrolls sideways, with its language above. */
@Composable
private fun CodeBlock(block: Blocks.Begin, style: TextStyle, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            block.language?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                block.inner,
                style = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize * 0.85f),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** A table in a grid that scrolls sideways; rows above the first rule are the header. */
@Composable
private fun OrgTable(
    table: Blocks.Table,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier,
    /** Given, the action of a tap on the field at a line of the table and a column, if it has one. */
    onCellTap: ((Int, Int) -> (() -> Unit)?)? = null,
    /** The field shown by [editor] instead of its text, with the style it would have. */
    editing: Pair<Int, Int>? = null,
    editor: @Composable (Int, Int, TextStyle) -> Unit = { _, _, _ -> },
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(8.dp)
    val rows = table.rows.withIndex().mapNotNull { (i, cells) -> cells?.let { i to it } }
    val header = table.headerRows
    // Columns of numbers align right, as Emacs writes them
    val numeric = remember(table) { Blocks.numericColumns(rows.drop(header).map { it.second }, table.columns) }
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .clip(shape)
            .border(1.dp, line, shape),
    ) {
        // Column by column, each as wide as its widest cell; cells are one line, so rows line up
        repeat(table.columns) { c ->
            Column(Modifier.width(IntrinsicSize.Max)) {
                rows.forEach { (i, cells) ->
                    val isHeader = i < header
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(onCellTap?.invoke(i, c)?.let { Modifier.clickable(onClick = it) } ?: Modifier)
                            .background(if (isHeader) headerColor else Color.Transparent)
                            .border(if (editing == i to c) 2.dp else 0.5.dp, if (editing == i to c) MaterialTheme.colorScheme.primary else line)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = if (numeric[c] && !isHeader) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        val cellStyle = if (isHeader) style.copy(fontWeight = FontWeight.Bold) else style
                        if (editing == i to c) {
                            editor(i, c, if (numeric[c] && !isHeader) cellStyle.copy(textAlign = TextAlign.End) else cellStyle)
                        } else {
                            LinkedText(cells.getOrElse(c) { "" }, cellStyle, onOpenLink, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
