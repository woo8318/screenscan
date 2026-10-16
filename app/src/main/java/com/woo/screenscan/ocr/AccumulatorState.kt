package com.woo.screenscan.ocr

/** Immutable captured-text state with a bounded undo history. */
data class AccumulatorState(
    val lines: List<TextLine> = emptyList(),
    val history: List<List<TextLine>> = emptyList(),
) {
    val text: String get() = lines.joinToString("\n") { it.text }

    fun merge(incoming: List<TextLine>): AccumulatorState {
        val merged = FrameMerger.merge(lines, incoming)
        return if (merged == lines) this else AccumulatorState(merged, pushHistory())
    }

    fun undo(): AccumulatorState =
        if (history.isEmpty()) this else AccumulatorState(history.last(), history.dropLast(1))

    fun clear(): AccumulatorState =
        if (lines.isEmpty()) this else AccumulatorState(emptyList(), pushHistory())

    /** Replaces the lines with user-edited text while keeping undo history. */
    fun replaceWith(text: String): AccumulatorState {
        val parsed = parseLines(text)
        return if (parsed == lines) this else AccumulatorState(parsed, pushHistory())
    }

    private fun pushHistory(): List<List<TextLine>> = (history + listOf(lines)).takeLast(MAX_HISTORY)

    companion object {
        private const val MAX_HISTORY = 50

        fun fromText(text: String): AccumulatorState = AccumulatorState(parseLines(text))

        private fun parseLines(text: String): List<TextLine> =
            if (text.isEmpty()) emptyList()
            else text.split('\n').map(TextLine::parse).dropLastWhile { it.isBlank }
    }
}
