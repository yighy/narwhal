package io.github.yighy.narwhal.core

import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Archiving as Emacs' org-archive-subtree does with its default location: the note and its
 * sub-notes go to the end of the notebook's archive file (gtd.org → gtd.org_archive), as a
 * top-level note with properties saying when and where from.
 */
object Archive {

    /** The archive file of the notebook at [path] in the folder. */
    fun path(path: String) = path + "_archive"

    /** What Emacs writes at the top of a new archive file. */
    fun header(fileName: String) = "#    -*- mode: org -*-\n\n\nArchived entries from file $fileName\n\n\n"

    /**
     * The subtree of note [index] of [doc] (the notebook at [path] in the folder) as it is
     * archived at [time]: ARCHIVE_TIME, ARCHIVE_FILE, ARCHIVE_OLPATH (its parents' titles, if
     * any), ARCHIVE_CATEGORY and ARCHIVE_TODO (its state, if any) added to its properties.
     */
    fun entry(doc: OrgDocument, index: Int, path: String, time: LocalDateTime): Subtree {
        val subtree = Outline.extract(doc, listOf(index)).single()
        val section = doc.sections[index]
        val head = doc.parseHead(section)
        val parents = mutableListOf<String>()
        var level = section.level
        for (i in index - 1 downTo 0) {
            if (doc.sections[i].level < level) {
                parents.add(0, doc.parseHead(doc.sections[i]).title.orEmpty().trim())
                level = doc.sections[i].level
            }
        }
        val properties = listOfNotNull(
            "ARCHIVE_TIME" to timeText(time),
            "ARCHIVE_FILE" to path,
            parents.takeIf { it.isNotEmpty() }?.let { "ARCHIVE_OLPATH" to it.joinToString("/") },
            "ARCHIVE_CATEGORY" to path.substringAfterLast('/').substringBeforeLast('.'),
            head.state?.let { "ARCHIVE_TODO" to it },
        )
        // The head's own lines are at the start of the subtree's text
        val headEnd = section.raw.length.coerceAtMost(subtree.text.length)
        val newHead = properties.fold(subtree.text.substring(0, headEnd)) { raw, (name, value) -> SectionEditor.addProperty(raw, name, value) }
        return Subtree(newHead + subtree.text.substring(headEnd), subtree.level)
    }

    /** The archive file's text ([archive], or a new one for [fileName]) with [entries] added at its end. */
    fun append(archive: String?, fileName: String, entries: List<Subtree>): String {
        val doc = OrgDocument.parse(archive ?: header(fileName))
        return Outline.insert(doc, doc.sections.size, 1, entries).text
    }

    /** As Org writes it: 2026-10-10 Sat 14:03. */
    private fun timeText(time: LocalDateTime): String =
        "%s %s %02d:%02d".format(time.toLocalDate(), time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), time.hour, time.minute)
}
