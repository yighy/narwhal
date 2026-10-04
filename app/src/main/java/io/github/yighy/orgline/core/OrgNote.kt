package io.github.yighy.orgline.core

/** A note as shown in the app, read from one section of an Org file. */
data class OrgNote(
    val fileUri: String,
    val fileName: String,
    val index: Int,
    val level: Int,
    val state: String?,
    val isDone: Boolean,
    val priority: String?,
    val title: String,
    val tags: List<String>,
    val scheduled: String?,
    val deadline: String?,
    val closed: String?,
    val properties: List<Pair<String, String>>,
    val content: String,
    /** Exact source text of the section, used to detect changes made by other apps. */
    val raw: String,
) {
    val id: String? get() = property("ID")

    fun property(name: String): String? =
        properties.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    fun toEdit() = NoteEdit(title, state, tags, properties, content, priority, scheduled, deadline, closed)

    companion object {
        fun fromDocument(doc: OrgDocument, fileUri: String, fileName: String): List<OrgNote> =
            doc.sections.mapIndexed { index, section ->
                val head = doc.parseHead(section)
                OrgNote(
                    fileUri = fileUri,
                    fileName = fileName,
                    index = index,
                    level = section.level,
                    state = head.state,
                    isDone = head.state != null && head.state in doc.keywords.done,
                    priority = head.priority,
                    title = head.title.orEmpty(),
                    tags = head.tags.orEmpty(),
                    scheduled = head.scheduled?.toString(),
                    deadline = head.deadline?.toString(),
                    closed = head.closed?.toString(),
                    properties = head.properties.all.map { it.name to it.value },
                    content = head.content.orEmpty().trimEnd(),
                    raw = section.raw,
                )
            }
    }
}

object NoteSearch {
    /** All words of [query] must appear in the title, content or tags. */
    fun matches(note: OrgNote, query: String, tag: String?): Boolean {
        if (tag != null && note.tags.none { it.equals(tag, ignoreCase = true) }) return false
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = buildString {
            append(note.title).append('\n').append(note.content).append('\n')
            note.tags.joinTo(this, " ")
        }
        return words.all { haystack.contains(it, ignoreCase = true) }
    }
}
