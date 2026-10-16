package com.woo.screenscan.ocr

import com.woo.screenscan.ocr.TestFixtures.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccumulatorStateTest {
    private fun AccumulatorState.mergeLines(incoming: List<TextLine>) = merge(incoming).state

    @Test
    fun mergeThenUndoRestoresPreviousLines() {
        val first = AccumulatorState().mergeLines(lines("alpha line one", "bravo line two"))
        val second = first.mergeLines(lines("bravo line two", "charlie line three"))
        assertEquals("alpha line one\nbravo line two\ncharlie line three", second.text)
        assertEquals(first.lines, second.undo().lines)
    }

    @Test
    fun clearKeepsUndo() {
        val state = AccumulatorState().mergeLines(lines("hello world"))
        val cleared = state.clear()
        assertEquals("", cleared.text)
        assertEquals(state.lines, cleared.undo().lines)
    }

    @Test
    fun replaceWithParsesIndentAndDropsTrailingBlankLines() {
        val state = AccumulatorState().replaceWith("    indented\nplain\n\n")
        assertEquals(listOf(TextLine(4, "indented"), TextLine(0, "plain")), state.lines)
    }

    @Test
    fun unchangedMergeReturnsSameInstance() {
        val state = AccumulatorState().mergeLines(lines("same line here"))
        val merged = state.merge(lines("same line here"))
        assertSame(state, merged.state)
        assertTrue(merged.result is FrameMerger.Result.Unchanged)
    }

    @Test
    fun rejectedOverlapLeavesStateUntouched() {
        val state = AccumulatorState().mergeLines(lines("alpha line one"))
        val merged = state.merge(lines("totally different"), requireOverlap = true)
        assertSame(state, merged.state)
        assertTrue(merged.result is FrameMerger.Result.NoOverlap)
    }

    @Test
    fun undoOnEmptyHistoryIsNoop() {
        val state = AccumulatorState()
        assertSame(state, state.undo())
    }
}
