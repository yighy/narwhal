package io.github.yighy.orgline.core

/** Org "id:" links: completion while typing, and parsing for display. */
object IdLinks {

    /** Text typed after "id:", from [start] (the "i") to [end] (the cursor). */
    data class Query(val start: Int, val end: Int, val text: String)

    data class Link(val range: IntRange, val id: String, val description: String?)

    private const val MAX_QUERY_LENGTH = 60

    private val LINK = Regex("""\[\[id:([^\]]+)](?:\[([^\]]*)])?]""")

    /**
     * Returns the "id:" query being typed just before [cursor], or null.
     * Ignores "id:" inside an existing link ("[[id:...") or in the middle of a word.
     */
    fun queryAt(text: String, cursor: Int): Query? {
        if (cursor < 3 || cursor > text.length) return null
        val before = text.substring(0, cursor)
        val start = before.lastIndexOf("id:")
        if (start < 0) return null

        val prev = before.getOrNull(start - 1)
        if (prev != null && !prev.isWhitespace() && prev != '(') return null

        val query = before.substring(start + 3)
        if (query.length > MAX_QUERY_LENGTH || query.any { it == '\n' || it == '[' || it == ']' }) return null

        return Query(start, cursor, query)
    }

    fun format(id: String, title: String): String {
        val description = title.replace('[', '(').replace(']', ')').trim()
        return if (description.isEmpty()) "[[id:$id]]" else "[[id:$id][$description]]"
    }

    fun find(text: String): List<Link> =
        LINK.findAll(text).map { m ->
            Link(m.range, m.groupValues[1].trim(), m.groupValues[2].ifEmpty { null })
        }.toList()

    /** Notes whose title contains every word of [query], best matches first. */
    fun suggestions(notes: List<OrgNote>, query: String, limit: Int = 20): List<OrgNote> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return notes
            .filter { note -> note.title.isNotBlank() && words.all { note.title.contains(it, ignoreCase = true) } }
            .sortedWith(
                compareByDescending<OrgNote> { words.isNotEmpty() && it.title.startsWith(words.first(), ignoreCase = true) }
                    .thenBy { it.title.length }
            )
            .take(limit)
    }
}
