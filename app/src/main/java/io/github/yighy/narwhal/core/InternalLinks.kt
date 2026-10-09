package io.github.yighy.narwhal.core

/**
 * Org's links within the notes, without a scheme: [[*Title]] (a note's title), [[#id]] (its
 * CUSTOM_ID), and [[name]]: a dedicated target <<name>>, an element named with #+NAME: name, or
 * else a note's title.
 */
object InternalLinks {

    private val SCHEME = Regex("""^[a-zA-Z][\w+.-]*:""")

    /** Whether [target] is such a link (not a URL, id:, file:…). */
    fun isInternal(target: String): Boolean = !SCHEME.containsMatchIn(target.trim())

    /** The note [target] leads to among [notes] (in the order to search), or null. */
    fun find(notes: List<OrgNote>, target: String): OrgNote? {
        val t = target.trim()
        return when {
            t.startsWith("*") -> notes.firstOrNull { sameTitle(it, t.substring(1)) }
            t.startsWith("#") -> notes.firstOrNull { it.property("CUSTOM_ID") == t.substring(1) }
            else -> notes.firstOrNull { "<<$t>>" in it.raw }
                ?: notes.firstOrNull { note -> NAME.findAll(note.content).any { it.groupValues[1].trim() == t } }
                ?: notes.firstOrNull { sameTitle(it, t) }
        }
    }

    private val NAME = Regex("""^[ \t]*#\+NAME:[ \t]*(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    // Titles compare without their links and cookies, as Org's search ignores them
    private fun sameTitle(note: OrgNote, title: String): Boolean =
        clean(IdLinks.plainText(note.title)).equals(clean(title), ignoreCase = true)

    private fun clean(text: String) = Cookies.COOKIE.replace(text, "").replace(Regex("\\s+"), " ").trim()
}
