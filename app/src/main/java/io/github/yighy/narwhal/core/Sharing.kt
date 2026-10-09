package io.github.yighy.narwhal.core

/** A note made from text shared by another app (a page's title and link, a selection…). */
object Sharing {

    data class Shared(val title: String, val content: String)

    private const val MAX_TITLE = 200

    /** Title and content from the shared [subject] (often a page title) and [text]. */
    fun note(subject: String?, text: String?): Shared? {
        val body = text?.replace("\r\n", "\n")?.trim().orEmpty()
        val heading = subject?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (heading.isNotEmpty()) {
            return Shared(heading.take(MAX_TITLE), if (body == heading) "" else body)
        }
        if (body.isEmpty()) return null
        val first = body.substringBefore('\n').trim()
        // A long first line stays whole in the content, its start as the title
        return if (first.length <= MAX_TITLE) {
            Shared(first, body.substringAfter('\n', "").trim())
        } else {
            Shared(first.take(MAX_TITLE).substringBeforeLast(' ') + "…", body)
        }
    }
}
