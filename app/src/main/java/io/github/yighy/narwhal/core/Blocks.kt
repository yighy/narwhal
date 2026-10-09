package io.github.yighy.narwhal.core

/**
 * A note's content split into what shows differently: plain text, tables, blocks
 * (#+BEGIN_SRC, #+BEGIN_QUOTE…) and drawers (:LOGBOOK:…:END:, not shown).
 * Each part has its [Block.range] of lines in the content.
 */
object Blocks {

    sealed interface Block {
        val range: IntRange
    }

    /** Text as written, starting at [range]'s start. */
    data class Text(override val range: IntRange, val text: String) : Block

    /** A table's rows; a null row is a rule (|---+---|). */
    data class Table(override val range: IntRange, val rows: List<List<String>?>) : Block {
        val columns: Int get() = rows.maxOf { it?.size ?: 0 }

        /** Rows above the first rule are the header, when there are rows below it. */
        val headerRows: Int
            get() {
                val rule = rows.indexOf(null)
                return if (rule > 0 && rows.drop(rule).any { it != null }) rule else 0
            }
    }

    /** #+BEGIN_[kind] … #+END_[kind]; [inner] starts at [innerStart] in the content. */
    data class Begin(override val range: IntRange, val kind: String, val args: String, val inner: String, val innerStart: Int) : Block {
        /** Source and example blocks show as code, as do blocks of an unknown kind. */
        val isCode: Boolean get() = kind !in setOf("QUOTE", "VERSE", "CENTER")
        val language: String? get() = args.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { kind == "SRC" && it.isNotEmpty() }
    }

    /** :[name]: … :END:; [inner] starts at [innerStart] in the content. */
    data class Drawer(override val range: IntRange, val name: String, val inner: String, val innerStart: Int) : Block

    private val BEGIN = Regex("""^[ \t]*#\+BEGIN_(\S+)(.*)$""", RegexOption.IGNORE_CASE)
    private val DRAWER_START = Regex("""^[ \t]*:([A-Za-z][\w-]*):[ \t]*$""")
    private val DRAWER_END = Regex("""^[ \t]*:END:[ \t]*$""", RegexOption.IGNORE_CASE)
    private val TABLE_LINE = Regex("""^[ \t]*\|.*$""")
    private val RULE = Regex("""^\|[-+:| ]*-[-+:| ]*\|?$""")

    fun parse(text: String): List<Block> {
        // Lines with where they start
        val starts = mutableListOf(0)
        text.forEachIndexed { i, c -> if (c == '\n') starts += i + 1 }
        val lines = starts.mapIndexed { i, s -> text.substring(s, if (i + 1 < starts.size) starts[i + 1] - 1 else text.length) }
        fun end(line: Int) = starts[line] + lines[line].length - 1

        val blocks = mutableListOf<Block>()
        var textFrom: Int? = null
        fun flushText(before: Int) {
            val from = textFrom ?: return
            textFrom = null
            val raw = text.substring(starts[from], starts[before] - 1).trimEnd()
            if (raw.isNotBlank()) blocks += Text(starts[from] until starts[from] + raw.length, raw)
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val begin = BEGIN.matchEntire(line)
            if (begin != null) {
                val kind = begin.groupValues[1].uppercase()
                val close = Regex("""^[ \t]*#\+END_${Regex.escape(kind)}[ \t]*$""", RegexOption.IGNORE_CASE)
                val last = (i + 1 until lines.size).firstOrNull { close.matches(lines[it]) }
                if (last != null) {
                    flushText(i)
                    val innerStart = if (last > i + 1) starts[i + 1] else starts[last]
                    val inner = if (last > i + 1) text.substring(starts[i + 1], starts[last] - 1) else ""
                    blocks += Begin(starts[i]..end(last), kind, begin.groupValues[2], inner, innerStart)
                    i = last + 1
                    continue
                }
            }
            val drawer = DRAWER_START.matchEntire(line)
            if (drawer != null && !drawer.groupValues[1].equals("END", ignoreCase = true)) {
                val last = (i + 1 until lines.size).firstOrNull { DRAWER_END.matches(lines[it]) }
                if (last != null) {
                    flushText(i)
                    val innerStart = if (last > i + 1) starts[i + 1] else starts[last]
                    val inner = if (last > i + 1) text.substring(starts[i + 1], starts[last] - 1) else ""
                    blocks += Drawer(starts[i]..end(last), drawer.groupValues[1].uppercase(), inner, innerStart)
                    i = last + 1
                    continue
                }
            }
            if (TABLE_LINE.matches(line)) {
                flushText(i)
                var last = i
                while (last + 1 < lines.size && TABLE_LINE.matches(lines[last + 1])) last++
                val rows = (i..last).map { row(lines[it]) }
                blocks += Table(starts[i]..end(last), rows)
                i = last + 1
                continue
            }
            if (textFrom == null) textFrom = i
            i++
        }
        if (textFrom != null) {
            val from = textFrom!!
            val raw = text.substring(starts[from]).trimEnd()
            if (raw.isNotBlank()) blocks += Text(starts[from] until starts[from] + raw.length, raw)
        }
        return blocks
    }

    /** Whether [text] shows as plain text only (no table, block or drawer). */
    fun isPlain(text: String): Boolean = parse(text).all { it is Text }

    /** Whether [text] shows nothing: blank, or only a logbook (shown as the clock's time). */
    fun showsNothing(text: String): Boolean = parse(text).all { it is Drawer && it.name == "LOGBOOK" }

    private fun row(line: String): List<String>? {
        val t = line.trim()
        if (RULE.matches(t)) return null
        return t.removePrefix("|").let { if (it.endsWith("|")) it.dropLast(1) else it }.split('|').map { it.trim() }
    }
}
