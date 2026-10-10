package io.github.yighy.narwhal.core

/**
 * Text split around the list items that can fold, as in Org: an item ("- …", "+ …", "1. …")
 * followed by lines indented further (its sub-items and text). Positions are from the text's
 * start.
 */
object ListFolds {

    sealed interface Part {
        val start: Int
    }

    data class Plain(override val start: Int, val text: String) : Part

    /** An item's first line, and what folds under it ([children], starting at [childrenStart]). */
    data class Item(override val start: Int, val line: String, val childrenStart: Int, val children: String) : Part

    private val ITEM = Regex("""^([ \t]*)(?:[-+]|\d+[.)])[ \t]+\S.*$""")

    fun parse(text: String): List<Part> {
        val split = Lines(text)
        val starts = split.starts
        val lines = split.lines
        fun indent(line: String) = line.takeWhile { it == ' ' || it == '\t' }.length

        val parts = mutableListOf<Part>()
        var plainFrom: Int? = null
        fun flush(before: Int) {
            val from = plainFrom ?: return
            plainFrom = null
            parts += Plain(starts[from], text.substring(starts[from], starts[before] - 1))
        }

        var i = 0
        while (i < lines.size) {
            val item = ITEM.matchEntire(lines[i])
            if (item != null) {
                val depth = item.groupValues[1].length
                // Lines below indented further, blank lines between them included
                var last = i
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || indent(lines[j]) > depth)) {
                    if (lines[j].isNotBlank()) last = j
                    j++
                }
                // Only with sub-items: an item whose text just goes on to the next line does not fold
                if (last > i && (i + 1..last).any { ITEM.matches(lines[it]) }) {
                    flush(i)
                    val childrenStart = starts[i + 1]
                    parts += Item(starts[i], lines[i], childrenStart, text.substring(childrenStart, starts[last] + lines[last].length))
                    i = last + 1
                    continue
                }
            }
            if (plainFrom == null) plainFrom = i
            i++
        }
        if (plainFrom != null) parts += Plain(starts[plainFrom!!], text.substring(starts[plainFrom!!]))
        return parts
    }

    /** Whether [text] has an item that can fold. */
    fun hasFolds(text: String): Boolean = parse(text).any { it is Item }
}
