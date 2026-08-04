package com.universalprinter.util

import com.universalprinter.model.Align
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth

/**
 * Turns a weighted, word-wrapped column row into monospace physical lines — rendered identically on
 * every backend (fixed-pitch Font A assumption). Cells are clamped to [PaperWidth.maxColumns], the
 * line's [PaperWidth.charsPerLine] is split by weight, each cell word-wraps to its width, and the
 * row spans as many lines as the tallest cell (shorter cells padded).
 */
internal object ColumnLayout {

    /** The composed physical lines for a column row. */
    fun format(cells: List<Column>, paper: PaperWidth): List<String> {
        if (cells.isEmpty()) return emptyList()
        val clamped = cells.take(paper.maxColumns)
        val widths = columnWidths(clamped, paper.charsPerLine)
        val wrapped = clamped.mapIndexed { i, c -> wrap(c.text, widths[i]) }
        val rows = wrapped.maxOf { it.size }
        return (0 until rows).map { r ->
            buildString {
                for (i in clamped.indices) append(pad(wrapped[i].getOrElse(r) { "" }, widths[i], clamped[i].align))
            }
        }
    }

    /** Split [total] chars across cells by weight, distributing any remainder left-to-right. */
    fun columnWidths(cells: List<Column>, total: Int): IntArray {
        val weights = cells.map { it.weight.coerceAtLeast(1) }
        val sum = weights.sum()
        val widths = IntArray(cells.size) { (total.toLong() * weights[it] / sum).toInt() }
        var used = widths.sum()
        var i = 0
        while (used < total && widths.isNotEmpty()) {
            widths[i % widths.size]++; used++; i++
        }
        return widths
    }

    /** Word-wrap [text] to [width]; breaks on whitespace, hard-breaks words longer than [width]. */
    fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) return listOf("")
        val out = ArrayList<String>()
        for (rawLine in text.split("\n")) {
            val words = rawLine.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) { out.add(""); continue }
            var cur = StringBuilder()
            for (word in words) {
                var w = word
                while (w.length > width) {
                    if (cur.isNotEmpty()) { out.add(cur.toString()); cur = StringBuilder() }
                    out.add(w.substring(0, width)); w = w.substring(width)
                }
                if (w.isEmpty()) continue
                when {
                    cur.isEmpty() -> cur.append(w)
                    cur.length + 1 + w.length <= width -> cur.append(' ').append(w)
                    else -> { out.add(cur.toString()); cur = StringBuilder(w) }
                }
            }
            if (cur.isNotEmpty()) out.add(cur.toString())
        }
        return out.ifEmpty { listOf("") }
    }

    private fun pad(s: String, width: Int, align: Align): String {
        val t = if (s.length > width) s.substring(0, width) else s
        return when (align) {
            Align.LEFT -> t.padEnd(width)
            Align.RIGHT -> t.padStart(width)
            Align.CENTER -> {
                val total = width - t.length
                val left = total / 2
                " ".repeat(left) + t + " ".repeat(total - left)
            }
        }
    }
}
