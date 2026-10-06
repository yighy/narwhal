package io.github.yighy.narwhal.core

/**
 * Links in note text, as Orgzly opens them: Org links `[[target][description]]` (any target:
 * id:, https:, file:…) and plain links such as https://…, mailto:, tel:, sms:, smsto:, mms:,
 * mmsto:, geo: and file: written in the text.
 */
object Links {

    data class Link(val range: IntRange, val target: String, val description: String?) {
        /** What the text shows for the link. */
        val shown: String get() = description ?: target
    }

    private val BRACKET = Regex("""\[\[([^\]]+)](?:\[([^\]]*)])?]""")
    private val PLAIN = Regex(
        """(?:https?://|mailto:|tel:|sms:|smsto:|mms:|mmsto:|geo:|file:)[^\s<>\[\]"]+""",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String): List<Link> {
        val bracket = BRACKET.findAll(text)
            .map { Link(it.range, it.groupValues[1].trim(), it.groupValues[2].ifEmpty { null }) }
            .toList()
        val plain = PLAIN.findAll(text)
            .filter { m -> bracket.none { m.range.first in it.range } }
            .mapNotNull { m ->
                // Punctuation ending the sentence is not part of the link (nor an unopened ")")
                var value = m.value.trimEnd('.', ',', ';', ':', '!', '?', '\'')
                if (value.endsWith(")") && '(' !in value) value = value.trimEnd(')')
                if (value.substringAfter(':').trimStart('/').isEmpty()) null
                else Link(m.range.first until m.range.first + value.length, value, null)
            }
        return (bracket + plain).sortedBy { it.range.first }
    }
}
