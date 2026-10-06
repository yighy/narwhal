package io.github.yighy.narwhal.core

/** Where a new or pasted note goes, relative to a note. */
enum class Placement { Above, Under, Below }

/** A note with all its sub-notes, as text, and the level of its first heading. */
data class Subtree(val text: String, val level: Int)

/** Structural edits on subtrees (a note and its sub-notes). Untouched text stays byte-identical. */
object Outline {

    private val HEADING = Regex("""^(\*+)([ \t])""", RegexOption.MULTILINE)

    /** Character range of the subtree of section [index]. */
    fun range(doc: OrgDocument, index: Int): IntRange {
        val start = doc.sections[index].start
        val end = doc.sections.getOrNull(doc.subtreeEnd(index))?.start ?: doc.text.length
        return start until end
    }

    /** [indices] without those inside the subtree of another one, in document order. */
    fun roots(doc: OrgDocument, indices: Collection<Int>): List<Int> {
        val sorted = indices.distinct().sorted()
        val result = mutableListOf<Int>()
        for (i in sorted) {
            if (result.none { root -> i > root && i < doc.subtreeEnd(root) }) result += i
        }
        return result
    }

    fun extract(doc: OrgDocument, indices: Collection<Int>): List<Subtree> =
        roots(doc, indices).map { i -> Subtree(withNewline(doc.text.substring(range(doc, i))), doc.sections[i].level) }

    fun delete(doc: OrgDocument, indices: Collection<Int>): OrgDocument {
        val sb = StringBuilder(doc.text)
        roots(doc, indices).reversed().forEach { i ->
            val r = range(doc, i)
            sb.delete(r.first, r.last + 1)
        }
        return doc.rebuild(sb.toString())
    }

    /** Inserts [subtrees] before section [atSection] (or at the end), their first heading at [level]. */
    fun insert(doc: OrgDocument, atSection: Int, level: Int, subtrees: List<Subtree>): OrgDocument {
        val offset = doc.sections.getOrNull(atSection)?.start ?: doc.text.length
        val sb = StringBuilder(doc.text.substring(0, offset))
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
        subtrees.forEach { sb.append(relevel(it.text, level - it.level)) }
        sb.append(doc.text.substring(offset))
        return doc.rebuild(sb.toString())
    }

    /** Section index and level where [Placement] relative to note [index] puts new notes. */
    fun target(doc: OrgDocument, index: Int, placement: Placement): Pair<Int, Int> {
        val level = doc.sections[index].level
        return when (placement) {
            Placement.Above -> index to level
            Placement.Under -> doc.subtreeEnd(index) to level + 1
            Placement.Below -> doc.subtreeEnd(index) to level
        }
    }

    /** Swaps the subtree with the previous sibling; returns the note's new index, or null if there is none. */
    fun moveUp(doc: OrgDocument, index: Int): Pair<OrgDocument, Int>? {
        val sibling = previousSibling(doc, index) ?: return null
        return swap(doc, sibling, index) to sibling
    }

    /** Swaps the subtree with the next sibling; returns the note's new index, or null if there is none. */
    fun moveDown(doc: OrgDocument, index: Int): Pair<OrgDocument, Int>? {
        val next = doc.subtreeEnd(index)
        if (next >= doc.sections.size || doc.sections[next].level != doc.sections[index].level) return null
        return swap(doc, index, next) to index + (doc.subtreeEnd(next) - next)
    }

    /** Changes the level of each subtree by [delta] (-1 promote, +1 demote), never above level 1. */
    fun shift(doc: OrgDocument, indices: Collection<Int>, delta: Int): OrgDocument {
        val roots = roots(doc, indices).filter { doc.sections[it].level + delta >= 1 }
        val sb = StringBuilder(doc.text)
        roots.reversed().forEach { i ->
            val r = range(doc, i)
            sb.replace(r.first, r.last + 1, relevel(doc.text.substring(r), delta))
        }
        return doc.rebuild(sb.toString())
    }

    /** Replaces several sections at once, e.g. to set the state of all selected notes. */
    fun replaceSections(doc: OrgDocument, newRaws: Map<Int, String>): OrgDocument {
        val sb = StringBuilder(doc.text)
        newRaws.keys.sortedDescending().forEach { i ->
            val s = doc.sections[i]
            sb.replace(s.start, s.end, newRaws.getValue(i))
        }
        return doc.rebuild(sb.toString())
    }

    private fun previousSibling(doc: OrgDocument, index: Int): Int? {
        val level = doc.sections[index].level
        for (j in index - 1 downTo 0) {
            val l = doc.sections[j].level
            if (l == level) return j
            if (l < level) return null
        }
        return null
    }

    /** Swaps the adjacent subtrees starting at [first] and [second]. */
    private fun swap(doc: OrgDocument, first: Int, second: Int): OrgDocument {
        val a = range(doc, first)
        val b = range(doc, second)
        val text = doc.text
        return doc.rebuild(
            text.substring(0, a.first) +
                withNewline(text.substring(b)) +
                withNewline(text.substring(a)) +
                text.substring(b.last + 1),
        )
    }

    private fun relevel(text: String, delta: Int): String =
        if (delta == 0) text else HEADING.replace(text) { m ->
            "*".repeat((m.groupValues[1].length + delta).coerceAtLeast(1)) + m.groupValues[2]
        }

    private fun withNewline(text: String) = if (text.isEmpty() || text.endsWith("\n")) text else "$text\n"
}
