package com.woo.screenscan.ocr

import com.woo.screenscan.ocr.TestFixtures.rawLine
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameExtractorTest {
    private val width = 1920
    private val height = 1080

    @Test
    fun reconstructsIndentAndInnerSpacing() {
        val raw = listOf(
            rawLine(0, 0 to "fun", 4 to "main()", 11 to "{"),
            rawLine(1, 4 to "val", 8 to "x", 10 to "=", 12 to "1"),
            rawLine(2, 0 to "}"),
        )
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(listOf("fun main() {", "    val x = 1", "}"), frame.lines.map { it.text })
    }

    @Test
    fun keepsMultipleSpacesForAlignedColumns() {
        val raw = listOf(
            rawLine(0, 0 to "INFO", 10 to "started"),
            rawLine(1, 0 to "ERROR", 10 to "failed"),
            rawLine(2, 0 to "WARN", 10 to "retry"),
        )
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals("INFO      started", frame.lines[0].text)
        assertEquals("ERROR     failed", frame.lines[1].text)
    }

    @Test
    fun mergesSplitBlocksOnTheSameRow() {
        val left = rawLine(0, 0 to "2026-10-17", 11 to "INFO")
        val right = rawLine(0, 20 to "worker", 27 to "ready")
        val frame = FrameExtractor.extract(listOf(right, left), width, height)
        assertEquals(1, frame.lines.size)
        assertEquals("2026-10-17 INFO     worker ready", frame.lines[0].text)
    }

    @Test
    fun dropsLinesTouchingImageEdges() {
        val cut = RawLine(listOf(TestFixtures.word("partial", 0f, 2f)), 0f, 2f, 70f, 22f)
        val raw = listOf(cut, rawLine(0, 0 to "whole"))
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(listOf("whole"), frame.lines.map { it.text })
    }

    @Test
    fun insertsBlankLinesFromVerticalGaps() {
        val raw = listOf(
            rawLine(0, 0 to "a"),
            rawLine(1, 0 to "b"),
            rawLine(3, 0 to "c"),
            rawLine(4, 0 to "d"),
        )
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(listOf("a", "b", "", "c", "d"), frame.lines.map { it.text })
    }

    @Test
    fun stripsLineNumbersWhenRequested() {
        val raw = listOf(
            rawLine(0, 0 to "12", 4 to "fun", 8 to "f()"),
            rawLine(1, 0 to "13", 6 to "x()"),
            rawLine(2, 0 to "14", 4 to "}"),
        )
        val frame = FrameExtractor.extract(raw, width, height, ExtractOptions(stripLineNumbers = true))
        assertEquals(listOf("fun f()", "  x()", "}"), frame.lines.map { it.text })
    }

    @Test
    fun usesFrameWideCharWidthForShortAndWideRows() {
        val top = 100f + TestFixtures.PITCH
        val hangul = OcrWord("한글", 80f, top, 120f, top + TestFixtures.LINE_H)
        val wideRow = RawLine(listOf(TestFixtures.word("val", 40f, top), hangul), 40f, top, 120f, top + TestFixtures.LINE_H)
        val tightBrace = RawLine(listOf(OcrWord("}", 40f, 160f, 45f, 180f)), 40f, 160f, 45f, 180f)
        val raw = listOf(rawLine(0, 0 to "fun main() {"), wideRow, tightBrace)
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(listOf("fun main() {", "    val 한글", "    }"), frame.lines.map { it.text })
    }

    @Test
    fun keepsLeadingNumbersWhenNotRequested() {
        val raw = listOf(
            rawLine(0, 0 to "12", 4 to "fun"),
            rawLine(1, 0 to "13", 4 to "x"),
            rawLine(2, 0 to "14", 4 to "}"),
        )
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals("12  fun", frame.lines[0].text)
    }
}
