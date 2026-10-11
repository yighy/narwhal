package io.github.yighy.orgwhal.core

/** [text]'s lines (without their "\n"), and where each one starts in it. */
internal class Lines(text: String) {
    val starts: List<Int> = buildList {
        add(0)
        text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
    }
    val lines: List<String> = starts.mapIndexed { i, s -> text.substring(s, if (i + 1 < starts.size) starts[i + 1] - 1 else text.length) }

    /** Position of the last character of line [line]. */
    fun end(line: Int) = starts[line] + lines[line].length - 1
}
