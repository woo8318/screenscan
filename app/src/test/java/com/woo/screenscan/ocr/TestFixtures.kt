package com.woo.screenscan.ocr

object TestFixtures {
    const val CHAR_W = 10f
    const val LINE_H = 20f
    const val PITCH = 30f

    fun word(text: String, left: Float, top: Float): OcrWord =
        OcrWord(text, left, top, left + text.length * CHAR_W, top + LINE_H)

    /** Builds one raw line from (column, text) tokens placed on a monospace grid. */
    fun rawLine(rowIndex: Int, vararg tokens: Pair<Int, String>): RawLine {
        val top = 100f + rowIndex * PITCH
        val words = tokens.map { (col, text) -> word(text, col * CHAR_W, top) }
        return RawLine(words, words.minOf { it.left }, top, words.maxOf { it.right }, top + LINE_H)
    }

    fun lines(vararg raw: String): List<TextLine> = raw.map(TextLine::parse)
}
