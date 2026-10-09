package io.github.yighy.narwhal.core

/**
 * Org's statistics cookies, [/] and [%], kept up to date as Emacs does by default: in a list
 * item they count its direct sub-items' checkboxes; in a heading, the top-level checkboxes of
 * the note, or else its direct sub-notes done among those with a state. A checkbox with
 * checkboxes under it shows [X] when all are checked, [-] when some are, [ ] when none.
 */
object Cookies {

    /** A cookie as written: [2/3], [/], [66%], [%]. */
    val COOKIE = Regex("""\[(\d*)(?:/(\d*)|(%))]""")

    private val ITEM = Regex("""^([ \t]*)(?:[-+*]|\d+[.)])[ \t]+(?:\[([ xX-])](?=[ \t]|$))?.*$""")

    private data class Item(val line: Int, val indent: Int, val box: Char?, val parent: Int?)

    /** [content] with its items' cookies and parent checkboxes updated. */
    fun updateContent(content: String): String {
        if (!content.contains('[')) return content
        val lines = content.split('\n').toMutableList()
        val items = items(lines)
        if (items.none { it.box != null }) return content
        val boxes = items.map { it.box }.toMutableList()
        // From the last item up, so sub-items are done before their parent
        for (k in items.indices.reversed()) {
            val children = items.indices.filter { items[it].parent == k && boxes[it] != null }
            if (children.isEmpty()) continue
            val checked = children.count { boxes[it] == 'X' || boxes[it] == 'x' }
            val partial = children.any { boxes[it] == '-' }
            var line = lines[items[k].line]
            if (boxes[k] != null) {
                val box = when {
                    checked == children.size -> 'X'
                    checked > 0 || partial -> '-'
                    else -> ' '
                }
                if (box != boxes[k]) {
                    val at = line.indexOf('[', items[k].indent)
                    line = line.substring(0, at + 1) + box + line.substring(at + 2)
                    boxes[k] = box
                }
            }
            lines[items[k].line] = fill(line, checked, children.size)
        }
        return lines.joinToString("\n")
    }

    /**
     * [title] with its cookies counting the top-level checkboxes of [content], or else the
     * [children] notes done among those with a state; unchanged when there is nothing to count.
     */
    fun updateTitle(title: String, content: String, children: List<OrgNote>): String {
        if (!COOKIE.containsMatchIn(title)) return title
        val lines = content.split('\n')
        val topBoxes = items(lines).filter { it.parent == null && it.box != null }
        return when {
            topBoxes.isNotEmpty() -> fill(title, topBoxes.count { it.box == 'X' || it.box == 'x' }, topBoxes.size)
            children.any { it.state != null } -> {
                val counted = children.filter { it.state != null }
                fill(title, counted.count { it.isDone }, counted.size)
            }
            else -> title
        }
    }

    /** Whether a heading's cookie counts its sub-notes (it has one, and no checkboxes to count). */
    fun countsSubNotes(note: OrgNote): Boolean =
        COOKIE.containsMatchIn(note.title) && items(note.content.split('\n')).none { it.parent == null && it.box != null }

    private fun fill(text: String, done: Int, total: Int): String = COOKIE.replace(text) { m ->
        if (m.groupValues[3] == "%") "[${if (total == 0) 0 else done * 100 / total}%]" else "[$done/$total]"
    }

    /** List items of [lines], each with the item it is under (the closest one indented less). */
    private fun items(lines: List<String>): List<Item> {
        val items = mutableListOf<Item>()
        // Items that later lines can be under, innermost last
        val open = ArrayDeque<Int>()
        lines.forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val indent = line.takeWhile { it == ' ' || it == '\t' }.length
            val m = ITEM.matchEntire(line)
            // A line indented as much as an item, or less, ends what is under it
            while (open.isNotEmpty() && items[open.last()].indent >= indent) open.removeLast()
            if (m == null) {
                // Text back at the margin ends the list
                if (indent == 0) open.clear()
                return@forEachIndexed
            }
            items += Item(i, indent, m.groups[2]?.value?.get(0), open.lastOrNull())
            open.addLast(items.size - 1)
        }
        return items
    }
}
