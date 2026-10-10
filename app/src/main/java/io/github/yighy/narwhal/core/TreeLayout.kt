package io.github.yighy.narwhal.core

/**
 * Places a notebook's notes as a tree drawn from left to right: the notebook at the root, each
 * note one column right of its parent, each leaf on a row of its own, and each parent across from
 * the middle of its children.
 */
object TreeLayout {

    /**
     * One box of the tree: the note at [index] (-1 for the notebook itself) in column [depth], on
     * [row] (half rows for parents of an even number of children), linked to the box at [parent]
     * in the result (-1 for the root). [hidden] notes are folded away under it.
     */
    data class Node(val index: Int, val depth: Int, val row: Float, val parent: Int, val hasChildren: Boolean, val hidden: Int)

    /** The boxes for notes of the given outline [levels], children of [folded] notes left out; root first. */
    fun layout(levels: List<Int>, folded: Set<Int> = emptySet()): List<Node> {
        // Children of each note, -1 being the root (a note under a deeper one's level jump still
        // goes under the nearest shallower note, as in the outline)
        val children = HashMap<Int, MutableList<Int>>()
        val stack = ArrayDeque<Int>()
        levels.forEachIndexed { i, level ->
            while (stack.isNotEmpty() && levels[stack.last()] >= level) stack.removeLast()
            children.getOrPut(stack.lastOrNull() ?: -1) { mutableListOf() } += i
            stack.addLast(i)
        }
        fun descendants(i: Int): Int = children[i].orEmpty().sumOf { 1 + descendants(it) }

        val result = mutableListOf<Node>()
        var nextRow = 0f
        fun place(index: Int, depth: Int, parent: Int): Float {
            val position = result.size
            val kids = children[index].orEmpty()
            val open = kids.isNotEmpty() && index !in folded
            result += Node(index, depth, 0f, parent, kids.isNotEmpty(), if (open) 0 else descendants(index))
            val row = if (open) {
                val rows = kids.map { place(it, depth + 1, position) }
                (rows.first() + rows.last()) / 2
            } else {
                nextRow++
            }
            result[position] = result[position].copy(row = row)
            return row
        }
        place(-1, 0, -1)
        return result
    }
}

/**
 * Keys of the notes that stay the same when a note changes in place (its state, a date, a
 * statistics cookie…), which changes its text: a note whose text is new takes the key of the note
 * that was at its place, if that one is gone and the notes are as many as before. Moved notes keep
 * their text, so their key.
 */
class TreeKeys {
    private var texts = emptyList<String>()
    private var keys = emptyList<String>()

    /** The keys of the notes whose texts (without heading stars, numbered among identical ones) are [new]. */
    fun update(new: List<String>): List<String> {
        if (new == texts) return keys
        val byText = texts.zip(keys).toMap()
        val gone = texts.toSet() - new.toSet()
        val used = HashSet<String>()
        val result = new.mapIndexed { i, text ->
            val key = byText[text]
                ?: texts.getOrNull(i)?.takeIf { new.size == texts.size && it in gone }?.let(byText::get)
                ?: text
            // A new note's text could be the key another note kept
            if (used.add(key)) key else "$key\u0000$i".also { used.add(it) }
        }
        texts = new
        keys = result
        return result
    }
}
