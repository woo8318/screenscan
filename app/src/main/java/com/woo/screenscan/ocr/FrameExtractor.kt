package com.woo.screenscan.ocr

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns recognizer output into ordered text lines with indentation, inner spacing and
 * blank lines reconstructed from word geometry. Pure Kotlin so it is unit-testable.
 */
object FrameExtractor {
    private const val EDGE_MARGIN_RATIO = 0.015f
    private const val SIDE_MARGIN_RATIO = 0.02f
    private const val ROW_TOLERANCE_RATIO = 0.5f
    private const val MIN_GAP_RATIO = 0.25f
    private const val MAX_SPACES = 120
    private const val MAX_BLANK_LINES = 5
    private const val MIN_ROWS_FOR_PITCH = 3
    private const val LINE_NUMBER_ROW_RATIO = 0.6f
    private const val MIN_CELLS_FOR_FRAME_WIDTH = 4
    private const val MIN_CELLS_FOR_OWN_WIDTH = 8
    private const val MIN_LINE_CONFIDENCE = 0.45f
    private const val MIN_FRAME_CONFIDENCE = 0.5f
    private const val MAX_TILT_DEGREES = 12f
    private val lineNumberPattern = Regex("^\\d{1,6}$")

    private data class Row(
        val words: List<OcrWord>,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val cells: Int,
        val ownCharWidth: Float?,
        val confidence: Float,
    ) {
        val centerY: Float get() = (top + bottom) / 2f
    }

    fun extract(
        rawLines: List<RawLine>,
        imageWidth: Int,
        imageHeight: Int,
        options: ExtractOptions = ExtractOptions(),
    ): Frame {
        val inBand = rawLines.filter { insideBand(it, imageHeight, options) }
        val angle = Deskew.medianAngle(inBand)
        val confidence = medianConfidence(inBand)
        val verdict = verdict(inBand, confidence, angle)
        val quality = FrameQuality(verdict, confidence, angle)
        if (verdict == FrameVerdict.EMPTY) return Frame(emptyList(), emptyList(), imageWidth, imageHeight, quality)

        val straightened = Deskew.rotate(inBand, angle, imageWidth, imageHeight)
        val margin = imageHeight * EDGE_MARGIN_RATIO
        val kept = straightened.filter {
            it.words.isNotEmpty() && it.confidence >= MIN_LINE_CONFIDENCE &&
                it.top > margin && it.bottom < imageHeight - margin
        }
        val rowGroups = groupIntoRows(kept)
        val rowWords = rowGroups.map { row -> row.flatMap { it.words }.sortedBy { it.left } }
        val rowConfidence = rowGroups.map { row -> row.minOf { it.confidence } }
        val cleaned = if (options.stripLineNumbers) stripLineNumbers(rowWords) else rowWords
        val rows = cleaned.zip(rowConfidence).filter { it.first.isNotEmpty() }.map { (words, conf) -> buildRow(words, conf) }
        val frameCharWidth = frameCharWidth(rows)
        val leftEdge = imageWidth * SIDE_MARGIN_RATIO
        val rightEdge = imageWidth * (1f - SIDE_MARGIN_RATIO)
        val leftMargin = rows.filter { it.left > leftEdge }.minOfOrNull { it.left } ?: 0f
        val lines = withBlankLines(rows) { row ->
            val charWidth = if (row.cells >= MIN_CELLS_FOR_OWN_WIDTH) row.ownCharWidth ?: frameCharWidth else frameCharWidth
            val cutLeft = row.left <= leftEdge
            val cutRight = row.right >= rightEdge
            val indent = if (cutLeft) 0 else ((row.left - leftMargin) / charWidth).roundToInt().coerceIn(0, MAX_SPACES)
            TextLine(indent, rowBody(row.words, charWidth), row.confidence, cutLeft, cutRight)
        }
        val boxes = rows.map { LineBox(it.left, it.top, it.right, it.bottom) }
        return Frame(lines, boxes, imageWidth, imageHeight, quality)
    }

    private fun insideBand(line: RawLine, imageHeight: Int, options: ExtractOptions): Boolean {
        val center = (line.top + line.bottom) / 2f
        return center >= options.bandTop * imageHeight && center <= options.bandBottom * imageHeight
    }

    private fun medianConfidence(lines: List<RawLine>): Float {
        if (lines.isEmpty()) return 0f
        val sorted = lines.map { it.confidence }.sorted()
        return sorted[sorted.size / 2]
    }

    private fun verdict(lines: List<RawLine>, confidence: Float, angle: Float): FrameVerdict = when {
        lines.isEmpty() -> FrameVerdict.EMPTY
        abs(angle) > MAX_TILT_DEGREES -> FrameVerdict.TILTED
        confidence < MIN_FRAME_CONFIDENCE -> FrameVerdict.BLURRY
        else -> FrameVerdict.OK
    }

    private fun groupIntoRows(lines: List<RawLine>): List<List<RawLine>> {
        val sorted = lines.sortedBy { (it.top + it.bottom) / 2f }
        return sorted.fold(emptyList<List<RawLine>>()) { rows, line ->
            val last = rows.lastOrNull()
            if (last != null && belongsToRow(last, line)) rows.dropLast(1) + listOf(last + line)
            else rows + listOf(listOf(line))
        }
    }

    private fun belongsToRow(row: List<RawLine>, line: RawLine): Boolean {
        val rowTop = row.minOf { it.top }
        val rowBottom = row.maxOf { it.bottom }
        val rowCenter = (rowTop + rowBottom) / 2f
        val lineCenter = (line.top + line.bottom) / 2f
        val tolerance = ROW_TOLERANCE_RATIO * minOf(rowBottom - rowTop, line.bottom - line.top)
        return abs(lineCenter - rowCenter) < tolerance
    }

    private fun stripLineNumbers(rows: List<List<OcrWord>>): List<List<OcrWord>> {
        if (rows.isEmpty()) return rows
        val numbered = rows.count { startsWithLineNumber(it) }
        if (numbered < rows.size * LINE_NUMBER_ROW_RATIO) return rows
        return rows.map { words -> if (startsWithLineNumber(words)) words.drop(1) else words }
    }

    private fun startsWithLineNumber(words: List<OcrWord>): Boolean =
        words.firstOrNull()?.text?.matches(lineNumberPattern) == true

    private fun buildRow(words: List<OcrWord>, confidence: Float): Row {
        val cells = words.sumOf { CharCells.count(it.text) }
        val totalWidth = words.sumOf { it.width.toDouble() }.toFloat()
        val ownCharWidth = if (cells > 0 && totalWidth > 0f) totalWidth / cells else null
        return Row(
            words = words,
            left = words.minOf { it.left },
            top = words.minOf { it.top },
            right = words.maxOf { it.right },
            bottom = words.maxOf { it.bottom },
            cells = cells,
            ownCharWidth = ownCharWidth,
            confidence = confidence,
        )
    }

    /** Median character cell width over rows long enough to measure reliably. */
    private fun frameCharWidth(rows: List<Row>): Float {
        val reliable = rows.filter { it.cells >= MIN_CELLS_FOR_FRAME_WIDTH }.mapNotNull { it.ownCharWidth }
        val candidates = reliable.ifEmpty { rows.mapNotNull { it.ownCharWidth } }.sorted()
        return if (candidates.isEmpty()) 1f else candidates[candidates.size / 2]
    }

    private fun rowBody(words: List<OcrWord>, charWidth: Float): String = buildString {
        words.forEachIndexed { index, word ->
            if (index > 0) append(" ".repeat(gapToSpaces(word.left - words[index - 1].right, charWidth)))
            append(word.text)
        }
    }

    private fun gapToSpaces(gap: Float, charWidth: Float): Int {
        if (gap < MIN_GAP_RATIO * charWidth) return 0
        return (gap / charWidth).roundToInt().coerceIn(1, MAX_SPACES)
    }

    /** Inserts blank lines where the vertical gap between rows is a multiple of the row pitch. */
    private fun withBlankLines(rows: List<Row>, toLine: (Row) -> TextLine): List<TextLine> {
        if (rows.size < MIN_ROWS_FOR_PITCH) return rows.map(toLine)
        val pitches = rows.zipWithNext { a, b -> b.centerY - a.centerY }.filter { it > 0f }.sorted()
        if (pitches.isEmpty()) return rows.map(toLine)
        val pitch = pitches[pitches.size / 2]
        return rows.flatMapIndexed { index, row ->
            val blanks = if (index == 0) 0 else blankLinesBetween(rows[index - 1], row, pitch)
            List(blanks) { TextLine.BLANK } + toLine(row)
        }
    }

    private fun blankLinesBetween(previous: Row, row: Row, pitch: Float): Int {
        if (pitch <= 0f) return 0
        val steps = ((row.centerY - previous.centerY) / pitch).roundToInt()
        return (steps - 1).coerceIn(0, MAX_BLANK_LINES)
    }
}
