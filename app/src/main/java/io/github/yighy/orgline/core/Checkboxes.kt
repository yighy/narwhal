package io.github.yighy.orgline.core

/** Org checkboxes in note content: list items such as "- [ ] milk", "- [X] bread", "1. [-] …". */
object Checkboxes {

    /**
     * One checkbox: [box] is the index of the character between the brackets; [range] covers
     * what a box replaces when shown (the bullet with the brackets, or the brackets after a number).
     */
    data class Box(val box: Int, val range: IntRange, val checked: Boolean, val partial: Boolean)

    private val ITEM = Regex("""(?m)^[ \t]*(?:([-+*])|\d+[.)])[ \t]+\[([ xX-])]""")

    fun find(text: String): List<Box> = ITEM.findAll(text).map { m ->
        val box = m.groups[2]!!.range.first
        val start = m.groups[1]?.range?.first ?: (box - 1)
        val mark = text[box]
        Box(box, start..(box + 1), checked = mark == 'x' || mark == 'X', partial = mark == '-')
    }.toList()

    /** [text] with its [n]th checkbox checked, or unchecked if it was. */
    fun toggle(text: String, n: Int): String {
        val box = find(text).getOrNull(n) ?: return text
        return text.substring(0, box.box) + (if (box.checked) ' ' else 'X') + text.substring(box.box + 1)
    }
}
