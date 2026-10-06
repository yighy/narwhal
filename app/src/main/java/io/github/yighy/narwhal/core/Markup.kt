package io.github.yighy.narwhal.core

/** Org emphasis in text: *bold*, /italic/, _underline_, +strike-through+, =verbatim=, ~code~. */
object Markup {

    enum class Style { Bold, Italic, Underline, Strike, Verbatim, Code }

    /** Emphasis over [range] (markers included); the text shown is [content]. */
    data class Emphasis(val range: IntRange, val style: Style, val content: String)

    private val STYLES = mapOf(
        '*' to Style.Bold, '/' to Style.Italic, '_' to Style.Underline,
        '+' to Style.Strike, '=' to Style.Verbatim, '~' to Style.Code,
    )

    // Org's rules: after the start, a space or one of -({'" ; the text neither starts nor ends
    // with a space, stays on one line; then a space, punctuation or the end
    private val EMPHASIS = Regex("""(?<=^|[\s\-({'"])([*/_+=~])(\S|\S[^\n]*?\S)\1(?=$|[\s\-.,:;!?'")}\[\]])""", RegexOption.MULTILINE)

    fun find(text: String): List<Emphasis> = EMPHASIS.findAll(text).mapNotNull { m ->
        val style = STYLES.getValue(m.groupValues[1][0])
        val content = m.groupValues[2]
        // "* item" is a list bullet, "--" or "++" are not emphasis
        if (content.all { it == m.groupValues[1][0] }) null else Emphasis(m.range, style, content)
    }.toList()
}
