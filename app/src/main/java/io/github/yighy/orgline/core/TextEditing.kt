package io.github.yighy.orgline.core

/**
 * Typing help for note text: lists that go on at a new line, brackets that close themselves,
 * and the line edits of the toolbar over the keyboard (list, checkbox, indent, insert).
 */
object TextEditing {

    /** Text with its selection ([start] to [end]; the cursor when equal). */
    data class Edit(val text: String, val start: Int, val end: Int = start) {
        val collapsed: Boolean get() = start == end
    }

    private val PAIRS = mapOf('[' to ']', '(' to ')', '{' to '}')

    /** List item at the start of a line: indentation, bullet or number, spaces, checkbox. */
    private val ITEM = Regex("""^([ \t]*)(?:([-+*])|(\d+)([.)]))([ \t]+)(\[[ xX-]][ \t]+)?""")

    /**
     * Adjusts what typing did ([old] became [new]), or returns null to keep it as is:
     * - a new line after a list item starts another one ("- [ ] " again, "2." after "1.");
     *   on an empty item it ends the list instead (with [lists] on)
     * - "[", "(" or "{" gets its closing pair, the cursor between them
     * - typing a closing one just before the same one steps over it
     * - deleting an opening one with its empty pair right after deletes both
     */
    fun afterTyping(old: Edit, new: Edit, lists: Boolean = true): Edit? {
        if (!old.collapsed || !new.collapsed) return null
        val p = old.start
        val o = old.text
        val n = new.text
        val typedOne = n.length == o.length + 1 && new.start == p + 1 &&
            n.regionMatches(0, o, 0, p) && n.regionMatches(p + 1, o, p, o.length - p)
        if (typedOne) {
            val c = n[p]
            return when {
                c == '\n' -> if (lists) continueList(o, p) else null
                c in PAIRS.values && o.getOrNull(p) == c -> Edit(o, p + 1)
                c in PAIRS -> Edit(n.substring(0, p + 1) + PAIRS.getValue(c) + n.substring(p + 1), p + 1)
                else -> null
            }
        }
        val deletedOne = p >= 1 && n.length == o.length - 1 && new.start == p - 1 &&
            n.regionMatches(0, o, 0, p - 1) && n.regionMatches(p - 1, o, p, o.length - p)
        if (deletedOne && PAIRS[o[p - 1]]?.let { it == o.getOrNull(p) } == true) {
            return Edit(n.removeRange(p - 1, p), p - 1)
        }
        return null
    }

    /** New line at [p] after a list item: another item, or the end of the list if it was empty. */
    private fun continueList(text: String, p: Int): Edit? {
        val lineStart = text.lastIndexOf('\n', p - 1) + 1
        val line = text.substring(lineStart, p)
        val m = ITEM.find(line) ?: return null
        if (line.substring(m.range.last + 1).isBlank()) {
            // An empty item: the list ends, the bullet goes
            return Edit(text.removeRange(lineStart, p), lineStart)
        }
        val (indent, bullet, number, dot, spaces, box) = m.destructured
        val next = indent + (bullet.ifEmpty { "${number.toInt() + 1}$dot" }) + spaces + (if (box.isNotEmpty()) "[ ] " else "")
        val insert = "\n" + next
        return Edit(text.substring(0, p) + insert + text.substring(p), p + insert.length)
    }

    /** Adds "- " to the lines, or takes the bullets away if they all have one. */
    fun toggleList(edit: Edit): Edit {
        val lines = targetLines(edit)
        val allItems = lines.all { ITEM.containsMatchIn(it) }
        return mapLines(edit) { line ->
            val m = ITEM.find(line)
            when {
                allItems && m != null -> line.removeRange(m.groupValues[1].length, m.value.length)
                !allItems && m == null && (line.isNotBlank() || lines.size == 1) -> insertAfterIndent(line, "- ")
                else -> line
            }
        }
    }

    /** Adds a checkbox to the lines (with a bullet if needed), or takes them away if they all have one. */
    fun toggleCheckbox(edit: Edit): Edit {
        val lines = targetLines(edit)
        val allBoxes = lines.all { ITEM.find(it)?.groupValues?.get(6)?.isNotEmpty() == true }
        return mapLines(edit) { line ->
            val m = ITEM.find(line)
            when {
                allBoxes && m != null -> line.removeRange(m.value.length - m.groupValues[6].length, m.value.length)
                allBoxes -> line
                m == null -> if (line.isNotBlank() || lines.size == 1) insertAfterIndent(line, "- [ ] ") else line
                m.groupValues[6].isEmpty() -> line.substring(0, m.value.length) + "[ ] " + line.substring(m.value.length)
                else -> line
            }
        }
    }

    /** Two more spaces before the lines. */
    fun indent(edit: Edit): Edit {
        val single = targetLines(edit).size == 1
        return mapLines(edit) { line -> if (line.isNotBlank() || single) "  $line" else line }
    }

    /** Two spaces (or a tab) less before the lines. */
    fun outdent(edit: Edit): Edit = mapLines(edit) { line ->
        when {
            line.startsWith("  ") -> line.substring(2)
            line.startsWith("\t") || line.startsWith(" ") -> line.substring(1)
            else -> line
        }
    }

    /** Puts [insert] in place of the selection, the cursor after it. */
    fun insert(edit: Edit, insert: String): Edit =
        Edit(edit.text.replaceRange(edit.start, edit.end, insert), edit.start + insert.length)

    private fun insertAfterIndent(line: String, prefix: String): String {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        return indent + prefix + line.substring(indent.length)
    }

    private fun lineBounds(edit: Edit): Pair<Int, Int> {
        val first = edit.text.lastIndexOf('\n', edit.start - 1) + 1
        val end = edit.text.indexOf('\n', edit.end).let { if (it < 0) edit.text.length else it }
        return first to end
    }

    /** Lines the selection touches (only non-blank ones, unless it is a single line). */
    private fun targetLines(edit: Edit): List<String> {
        val (first, end) = lineBounds(edit)
        val lines = edit.text.substring(first, end).split('\n')
        return if (lines.size == 1) lines else lines.filter { it.isNotBlank() }.ifEmpty { lines }
    }

    /**
     * Changes each line the selection touches; the selection follows the text (a position in
     * a changed part goes to its end).
     */
    private fun mapLines(edit: Edit, change: (String) -> String): Edit {
        val text = edit.text
        val (first, end) = lineBounds(edit)
        val out = StringBuilder(text.substring(0, first))
        var start = edit.start
        var stop = edit.end
        var oldLineStart = first
        text.substring(first, end).split('\n').forEachIndexed { i, line ->
            if (i > 0) out.append('\n')
            val newLineStart = out.length
            val new = change(line)
            out.append(new)
            val prefix = line.commonPrefixWith(new).length
            val suffix = minOf(line.commonSuffixWith(new).length, line.length - prefix, new.length - prefix)
            // At the edge of what changed, the cursor and a selection's end go after it (text
            // inserted at the cursor), a selection's start stays before it
            fun move(pos: Int, after: Boolean): Int {
                val o = pos - oldLineStart
                if (o < 0 || o > line.length) return pos
                val inNew = when {
                    o < prefix || (o == prefix && !after) -> o
                    o >= line.length - suffix -> o + new.length - line.length
                    else -> new.length - suffix
                }
                return newLineStart + inNew
            }
            val range = oldLineStart..oldLineStart + line.length
            if (edit.start in range) start = move(edit.start, after = edit.collapsed)
            if (edit.end in range) stop = move(edit.end, after = true)
            oldLineStart += line.length + 1
        }
        val shift = out.length - end
        out.append(text.substring(end))
        if (edit.start > end) start += shift
        if (edit.end > end) stop += shift
        return Edit(out.toString(), start, stop)
    }
}
