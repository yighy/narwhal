package io.github.yighy.orgline.core

/** Outline structure of the notes of one file, which are in document order. */
object NoteTree {

    fun hasChildren(notes: List<OrgNote>, index: Int): Boolean =
        (notes.getOrNull(index + 1)?.level ?: 0) > notes[index].level

    /** Ancestors of the note at [index], outermost first. */
    fun ancestors(notes: List<OrgNote>, index: Int): List<OrgNote> {
        val result = mutableListOf<OrgNote>()
        var level = notes[index].level
        for (i in index - 1 downTo 0) {
            if (notes[i].level < level) {
                result.add(0, notes[i])
                level = notes[i].level
            }
        }
        return result
    }

    /** For each note, the levels of its ancestors, outermost first (one pass with a stack). */
    fun ancestorLevels(notes: List<OrgNote>): List<List<Int>> {
        val stack = ArrayDeque<Int>()
        return notes.map { note ->
            while (stack.isNotEmpty() && stack.last() >= note.level) stack.removeLast()
            val levels = stack.toList()
            stack.addLast(note.level)
            levels
        }
    }

    /** Notes not hidden under a folded ancestor. */
    fun visible(notes: List<OrgNote>, isFolded: (OrgNote) -> Boolean): List<OrgNote> {
        val result = mutableListOf<OrgNote>()
        var hideBelow = Int.MAX_VALUE
        for (note in notes) {
            if (note.level > hideBelow) continue
            hideBelow = Int.MAX_VALUE
            result += note
            if (isFolded(note)) hideBelow = note.level
        }
        return result
    }
}

/** A notebook is one .org file. */
object Notebooks {
    private val TITLE = Regex("""^#\+TITLE:[ \t]*(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val KEYWORD_LINE = Regex("""^[ \t]*#\+\S+:.*$""")

    /** #+TITLE of the file, or its name without extension. */
    fun title(doc: OrgDocument, fileName: String): String =
        TITLE.find(doc.preface)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.removeSuffix(".org").removeSuffix(".ORG")

    /** Preface text without #+KEYWORD lines. */
    fun prefaceText(doc: OrgDocument): String =
        doc.preface.lines().filterNot { KEYWORD_LINE.matches(it) }.joinToString("\n").trim()
}
