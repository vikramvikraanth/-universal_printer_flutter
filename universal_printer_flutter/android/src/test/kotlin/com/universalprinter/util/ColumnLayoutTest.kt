package com.universalprinter.util

import com.universalprinter.model.Align
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnLayoutTest {

    @Test
    fun columnWidthsSplitByWeightAndSumToTotal() {
        assertEquals(listOf(16, 16), ColumnLayout.columnWidths(listOf(Column("a"), Column("b")), 32).toList())
        assertEquals(listOf(24, 8), ColumnLayout.columnWidths(listOf(Column("a", 3), Column("b", 1)), 32).toList())
        assertEquals(32, ColumnLayout.columnWidths(listOf(Column("a"), Column("b"), Column("c")), 32).sum())
    }

    @Test
    fun wrapBreaksOnSpacesHardBreaksLongWordsAndKeepsNewlines() {
        assertEquals(listOf("hello", "world"), ColumnLayout.wrap("hello world", 5))
        assertEquals(listOf("abc", "def", "gh"), ColumnLayout.wrap("abcdefgh", 3))
        assertEquals(listOf("a", "b"), ColumnLayout.wrap("a\nb", 10))
    }

    @Test
    fun twoColumnRowIsLeftRightAlignedToWidth() {
        val lines = ColumnLayout.format(listOf(Column("Coffee", 1, Align.LEFT), Column("3.50", 1, Align.RIGHT)), PaperWidth.MM_80)
        assertEquals(1, lines.size)
        assertEquals(48, lines[0].length)
        assertTrue(lines[0].startsWith("Coffee"))
        assertTrue(lines[0].endsWith("3.50"))
    }

    @Test
    fun clampsToPaperMaxColumns() {
        val cells = listOf("A", "B", "C", "D", "E", "F").map { Column(it) } // 6 cells; MM_80 max = 5
        val line = ColumnLayout.format(cells, PaperWidth.MM_80)[0]
        assertTrue(line.contains("A") && line.contains("E"))
        assertFalse(line.contains("F"))
    }

    @Test
    fun rowSpansMultipleLinesWhenACellWraps() {
        val lines = ColumnLayout.format(
            listOf(Column("alpha beta gamma", 1, Align.LEFT), Column("x", 1), Column("y", 1)),
            PaperWidth.MM_58, // 32 chars / 3 cols
        )
        assertEquals(2, lines.size)
    }
}
