package io.github.yighy.narwhal.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.TextButton
import io.github.yighy.narwhal.core.TableFormulas
import androidx.compose.ui.res.stringResource
import io.github.yighy.narwhal.R
import io.github.yighy.narwhal.core.ListFolds
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
import io.github.yighy.narwhal.core.Blocks
import io.github.yighy.narwhal.core.Checkboxes

/**
 * A note's content as Org shows it: text with its links and checkboxes ([LinkedText]), tables,
 * source and example blocks in a monospace box, quotes with a bar; drawers (:LOGBOOK:…) hidden.
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
                // The logbook shows as the clock's time; other drawers open with a tap
                is Blocks.Drawer -> if (block.name != "LOGBOOK") {
                    DrawerBlock(block, style, onOpenLink, tapFrom(block.innerStart), toggleFrom(block.innerStart))
                }
                is Blocks.Text -> FoldableText(block.text, block.range.first, style, onOpenLink, ::tapFrom, ::toggleFrom)
                is Blocks.Table -> Column {
                    block.name?.let { ElementName(it) }
                    OrgTable(block, style, onOpenLink, Modifier.tapToEdit(onTextTap, block.range.first))
                    if (block.formulaLines.isNotEmpty()) {
                        FormulaBar(block, onRecalculate?.let { done ->
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
                    val rotation by animateFloatAsState(if (open) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "item")
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
                    val motion = MaterialTheme.motionScheme
                    AnimatedVisibility(
                        open,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
                    ) {
                        FoldableText(part.children, start + part.childrenStart, style, onOpenLink, tapFrom, toggleFrom)
                    }
                }
            }
        }
    }
}

/** A table's formulas, folded to "ƒ 2 formulas", with a button to recalculate the table. */
@Composable
private fun FormulaBar(table: Blocks.Table, onRecalculate: (() -> Unit)?) {
    var open by rememberSaveable(table.range.first) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "formulas")
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
                TextButton(onClick = onRecalculate) { Text(stringResource(R.string.recalculate)) }
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            open,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
        ) {
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
    val rotation by animateFloatAsState(if (open) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "drawer")
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
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            open,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
        ) {
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
private fun OrgTable(table: Blocks.Table, style: TextStyle, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(8.dp)
    val rows = table.rows.withIndex().mapNotNull { (i, cells) -> cells?.let { i to it } }
    val header = table.headerRows
    // Columns of numbers align right, as Emacs writes them
    val numeric = remember(table) {
        (0 until table.columns).map { c ->
            val cells = rows.drop(header).map { it.second.getOrElse(c) { "" } }.filter { it.isNotEmpty() }
            cells.isNotEmpty() && cells.count { it.toDoubleOrNull() != null } * 2 >= cells.size
        }
    }
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
                            .background(if (isHeader) headerColor else Color.Transparent)
                            .border(0.5.dp, line)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = if (numeric[c] && !isHeader) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        LinkedText(
                            cells.getOrElse(c) { "" },
                            if (isHeader) style.copy(fontWeight = FontWeight.Bold) else style,
                            onOpenLink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
