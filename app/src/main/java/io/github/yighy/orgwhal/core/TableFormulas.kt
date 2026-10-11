package io.github.yighy.orgwhal.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * The common part of Org's table formulas (#+TBLFM:), computed as Emacs does:
 * - column formulas ($4=…), for the rows below the header, and field formulas (@3$4=…, @>$4=…);
 * - references $2, @3, @3$2, @>, @<, $>, $<, relative @-1, $+1, ranges @2..@-1, @I..@II, $2..$4;
 * - numbers, + - * / ^, parentheses, vsum, vmean, vmin, vmax, vcount, abs;
 * - remote(name, ref): a field or range of another table, named with #+NAME (or the first table
 *   of the note with that ID), found by [recalculate]'s remote;
 * - formats ;%.2f, ;%d (and the N and E flags).
 * Anything else (Lisp, Calc's other functions, names…) is left alone: those formulas are skipped,
 * so a value is never replaced by a wrong one.
 */
object TableFormulas {

    /** The table recalculated, realigned if a field changed, and the formulas skipped. */
    data class Result(val lines: List<String>, val skipped: Int)

    private class Unsupported : Exception()

    /** A table's rows: data rows (cells) and rules (null). */
    private class Table(val rows: MutableList<MutableList<String>?>) {
        val dataRows: List<Int> get() = rows.indices.filter { rows[it] != null }
        val columns: Int get() = rows.maxOf { it?.size ?: 0 }

        /** Data row number (from 1, rules not counted) of row index [i]. */
        fun number(i: Int) = dataRows.indexOf(i) + 1

        fun cell(row: Int, col: Int): String = rows[dataRows[row - 1]]!!.getOrElse(col - 1) { "" }

        fun set(row: Int, col: Int, value: String) {
            val cells = rows[dataRows[row - 1]]!!
            while (cells.size < col) cells += ""
            cells[col - 1] = value
        }

        /** Data row numbers after the [n]th rule ([after]) or before it. */
        fun ruleRow(n: Int, after: Boolean): Int {
            val rules = rows.indices.filter { rows[it] == null }
            val at = rules.getOrNull(n - 1) ?: throw Unsupported()
            val row = if (after) dataRows.firstOrNull { it > at } else dataRows.lastOrNull { it < at }
            return row?.let { number(it) } ?: throw Unsupported()
        }
    }

    private sealed interface Value
    private data class Num(val v: Double) : Value
    private data class Vec(val v: List<Double>) : Value

    private val TBLFM = Regex("""^[ \t]*#\+TBLFM:[ \t]*(.*)$""", RegexOption.IGNORE_CASE)

    /** Formulas written on #+TBLFM lines, in order. */
    fun formulas(lines: List<String>): List<String> =
        lines.mapNotNull { TBLFM.matchEntire(it)?.groupValues?.get(1) }.flatMap { it.split("::") }.map { it.trim() }.filter { it.isNotEmpty() }

    /** [tableLines] (| rows |) recalculated with [formulas]; [remote] gives the lines of another table by name. */
    fun recalculate(tableLines: List<String>, formulas: List<String>, remote: (String) -> List<String>? = { null }): Result {
        val table = Table(tableLines.map { parseRow(it) }.toMutableList())
        if (table.dataRows.isEmpty()) return Result(tableLines, formulas.size)
        val before = table.rows.map { it?.toList() }
        var skipped = 0

        // Field formulas override column formulas, which skip their fields
        val parsed = formulas.map { f ->
            val eq = f.indexOf('=')
            if (eq <= 0) null else f.substring(0, eq).trim() to f.substring(eq + 1).trim()
        }
        val fieldTargets = parsed.mapNotNull { it?.first }.mapNotNull { target ->
            runCatching { fieldTarget(table, target) }.getOrNull()
        }.toSet()

        val rules = table.rows.indices.filter { table.rows[it] == null }
        // Rows of the header (above the first rule) are not computed by column formulas
        val firstBody = rules.firstOrNull()?.takeIf { r -> table.dataRows.any { it < r } && table.dataRows.any { it > r } }
            ?.let { r -> table.number(table.dataRows.first { it > r }) } ?: 1

        // Column formulas first, as Emacs does, then field formulas
        val ordered = parsed.withIndex().sortedBy { (_, p) -> if (p != null && p.first.startsWith("$")) 0 else 1 }
        for ((_, p) in ordered) {
            if (p == null) { skipped++; continue }
            val (target, expression) = p
            try {
                val (expr, format) = splitFormat(expression)
                if (target.startsWith("$")) {
                    val col = column(table, target.removePrefix("$"), current = null)
                    val results = (firstBody..table.dataRows.size)
                        .filter { (it to col) !in fieldTargets }
                        .map { row -> row to Parser(expr, table, row, col, remote).parse() }
                    results.forEach { (row, v) -> table.set(row, col, format(v, format)) }
                } else {
                    val (row, col) = fieldTarget(table, target)
                    table.set(row, col, format(Parser(expr, table, row, col, remote).parse(), format))
                }
            } catch (_: Unsupported) {
                skipped++
            } catch (_: ArithmeticException) {
                skipped++
            }
        }
        val changed = table.rows.map { it?.toList() } != before
        return Result(if (changed) align(table, indent(tableLines.first())) else tableLines, skipped)
    }

    /**
     * A remote() lookup whose tables are recalculated first, with the tables they read in turn, up
     * to [depth] tables deep (tables reading each other stop there); [find] gives a table's lines and
     * formulas by its name.
     */
    fun freshRemote(find: (String) -> Pair<List<String>, List<String>>?, depth: Int = 5): (String) -> List<String>? {
        fun fresh(name: String, left: Int): List<String>? {
            val (lines, formulas) = find(name) ?: return null
            if (formulas.isEmpty() || left == 0) return lines
            return recalculate(lines, formulas) { fresh(it, left - 1) }.lines
        }
        return { fresh(it, depth) }
    }

    // ---- Targets and references ----

    private fun fieldTarget(table: Table, target: String): Pair<Int, Int> {
        val m = Regex("""^@([<>]|\d+|I+)\$([<>]|\d+)$""").matchEntire(target) ?: throw Unsupported()
        return row(table, m.groupValues[1], current = null) to column(table, m.groupValues[2], current = null)
    }

    private fun row(table: Table, ref: String, current: Int?): Int = when {
        ref == "<" -> 1
        ref == ">" -> table.dataRows.size
        ref.startsWith("+") || ref.startsWith("-") -> (current ?: throw Unsupported()) + ref.toInt()
        ref.all { it.isDigit() } -> ref.toInt()
        else -> throw Unsupported()
    }.also { if (it < 1 || it > table.dataRows.size) throw Unsupported() }

    private fun column(table: Table, ref: String, current: Int?): Int = when {
        ref == "<" -> 1
        ref == ">" -> table.columns
        ref.startsWith("+") || ref.startsWith("-") -> (current ?: throw Unsupported()) + ref.toInt()
        ref.isNotEmpty() && ref.all { it.isDigit() } -> ref.toInt()
        else -> throw Unsupported()
    }.also { if (it < 1 || it > table.columns) throw Unsupported() }

    private fun number(cell: String): Double? {
        val t = cell.trim()
        if (t.isEmpty()) return null
        return t.toDoubleOrNull() ?: throw Unsupported()
    }

    /** Expression and output format, from "expr;%.2f" (flags N and E accepted). */
    private fun splitFormat(expression: String): Pair<String, String?> {
        val semi = expression.lastIndexOf(';')
        if (semi < 0) return expression to null
        val modes = expression.substring(semi + 1).trim()
        val format = Regex("""%(\.\d+)?[fd]""").find(modes)?.value
        val rest = modes.replace(format ?: "", "")
        if (rest.any { it !in "NE" }) throw Unsupported()
        return expression.substring(0, semi) to format
    }

    private fun format(value: Value, format: String?): String {
        val v = (value as? Num)?.v ?: throw Unsupported()
        if (v.isNaN() || v.isInfinite()) throw ArithmeticException()
        return when {
            format == "%d" -> Math.round(v).toString()
            format != null -> String.format(Locale.ROOT, format, v)
            // As Calc shows numbers: integers whole, others with up to 12 significant digits
            v == floor(v) && abs(v) < 1e15 -> v.toLong().toString()
            else -> "%.12g".format(Locale.ROOT, v).let { s ->
                if ('e' in s) s else s.trimEnd('0').trimEnd('.')
            }
        }
    }

    // ---- Expressions ----

    private class Parser(val text: String, val table: Table, val row: Int, val col: Int, val remote: (String) -> List<String>?) {
        private var pos = 0

        fun parse(): Value {
            val v = expr()
            skip()
            if (pos != text.length) throw Unsupported()
            return v
        }

        private fun skip() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skip()
            return text.getOrNull(pos)
        }

        private fun expr(): Value {
            var left = term()
            while (true) {
                left = when (peek()) {
                    '+' -> { pos++; arith(left, term()) { a, b -> a + b } }
                    '-' -> { pos++; arith(left, term()) { a, b -> a - b } }
                    else -> return left
                }
            }
        }

        private fun term(): Value {
            var left = power()
            while (true) {
                left = when (peek()) {
                    '*' -> { pos++; arith(left, power()) { a, b -> a * b } }
                    '/' -> { pos++; arith(left, power()) { a, b -> if (b == 0.0) throw ArithmeticException() else a / b } }
                    else -> return left
                }
            }
        }

        private fun power(): Value {
            val base = unary()
            if (peek() == '^') {
                pos++
                return arith(base, power()) { a, b -> a.pow(b) }
            }
            return base
        }

        private fun unary(): Value = if (peek() == '-') {
            pos++
            arith(Num(0.0), unary()) { a, b -> a - b }
        } else {
            primary()
        }

        private fun primary(): Value {
            val c = peek() ?: throw Unsupported()
            return when {
                c == '(' -> {
                    pos++
                    expr().also { if (peek() != ')') throw Unsupported(); pos++ }
                }
                c.isDigit() || c == '.' -> {
                    val start = pos
                    while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
                    Num(text.substring(start, pos).toDoubleOrNull() ?: throw Unsupported())
                }
                c == '@' || c == '$' -> reference()
                c.isLetter() -> function()
                else -> throw Unsupported()
            }
        }

        private fun function(): Value {
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            val name = text.substring(start, pos)
            if (peek() != '(') throw Unsupported()
            pos++
            if (name == "remote") return remote()
            val arg = expr()
            if (peek() != ')') throw Unsupported()
            pos++
            val values = when (arg) {
                is Vec -> arg.v
                is Num -> listOf(arg.v)
            }
            return Num(
                when (name) {
                    "vsum" -> values.sum()
                    "vmean" -> if (values.isEmpty()) throw ArithmeticException() else values.average()
                    "vmin" -> values.minOrNull() ?: throw ArithmeticException()
                    "vmax" -> values.maxOrNull() ?: throw ArithmeticException()
                    "vcount" -> values.size.toDouble()
                    "abs" -> abs((arg as? Num)?.v ?: throw Unsupported())
                    else -> throw Unsupported()
                },
            )
        }

        /** remote(name, ref), after its "(": the field or range [ref] of the table named name. */
        private fun remote(): Value {
            val close = text.indexOf(')', pos)
            val comma = text.indexOf(',', pos)
            if (close < 0 || comma < 0 || comma > close) throw Unsupported()
            val name = text.substring(pos, comma).trim()
            val ref = text.substring(comma + 1, close).trim()
            pos = close + 1
            val lines = remote(name) ?: throw Unsupported()
            val other = Table(lines.map { parseRow(it) }.toMutableList())
            if (other.dataRows.isEmpty()) throw Unsupported()
            // Only absolute references: there is no current field in the other table
            if (Regex("""[@$][+-]""").containsMatchIn(ref)) throw Unsupported()
            return Parser(ref, other, 1, 1, remote).parse()
        }

        /** A field ($2, @3$2…), or a range (…..…) as a vector without its empty fields. */
        private fun reference(): Value {
            val first = ref()
            if (text.startsWith("..", pos)) {
                pos += 2
                val second = ref()
                val r1 = first.row ?: row
                val r2 = second.row ?: row
                val c1 = first.col ?: col
                val c2 = second.col ?: col
                val values = (minOf(r1, r2)..maxOf(r1, r2)).flatMap { r ->
                    (minOf(c1, c2)..maxOf(c1, c2)).mapNotNull { c -> number(table.cell(r, c)) }
                }
                return Vec(values)
            }
            if (first.rule != null) throw Unsupported()
            return Num(number(table.cell(first.row ?: row, first.col ?: col)) ?: 0.0)
        }

        private class Ref(val row: Int?, val col: Int?, val rule: Int?)

        private fun ref(): Ref {
            val m = REF.matchAt(text, pos) ?: throw Unsupported()
            if (m.value.isEmpty()) throw Unsupported()
            pos += m.value.length
            val rowRef = m.groups[1]?.value
            val colRef = m.groups[2]?.value
            // @I, @II: in a range, the rows just after (as a start) or before (as an end) that rule
            val rule = rowRef?.takeIf { it.all { c -> c == 'I' } }?.length
            val r = when {
                rowRef == null -> null
                rule != null -> table.ruleRow(rule, after = !text.substring(0, pos - m.value.length).endsWith(".."))
                else -> row(table, rowRef, row)
            }
            val c = colRef?.let { column(table, it, col) }
            return Ref(r, c, rule)
        }

        private fun arith(a: Value, b: Value, op: (Double, Double) -> Double): Value {
            if (a !is Num || b !is Num) throw Unsupported()
            return Num(op(a.v, b.v))
        }

        companion object {
            private val REF = Regex("""(?:@([<>]|I+|[+-]?\d+))?(?:\$([<>]|[+-]?\d+))?""")
        }
    }

    // ---- Layout ----

    private fun parseRow(line: String): MutableList<String>? = Blocks.tableRow(line)?.toMutableList()

    private fun indent(line: String) = line.takeWhile { it == ' ' || it == '\t' }

    /** The table written back as Emacs aligns it: numbers to the right, text to the left. */
    private fun align(table: Table, indent: String): List<String> {
        val columns = table.columns
        val data = table.rows.filterNotNull()
        val widths = (0 until columns).map { c -> data.maxOf { it.getOrElse(c) { "" }.length }.coerceAtLeast(1) }
        val numeric = Blocks.numericColumns(data, columns)
        return table.rows.map { cells ->
            if (cells == null) {
                indent + "|" + widths.joinToString("+") { "-".repeat(it + 2) } + "|"
            } else {
                indent + "|" + (0 until columns).joinToString("|") { c ->
                    val v = cells.getOrElse(c) { "" }
                    " " + (if (numeric[c]) v.padStart(widths[c]) else v.padEnd(widths[c])) + " "
                } + "|"
            }
        }
    }
}
