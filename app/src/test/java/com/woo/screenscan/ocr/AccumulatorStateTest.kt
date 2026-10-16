package com.woo.screenscan.ocr

import com.woo.screenscan.ocr.TestFixtures.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AccumulatorStateTest {
    @Test
    fun mergeThenUndoRestoresPreviousLines() {
        val first = AccumulatorState().merge(lines("alpha line one", "bravo line two"))
        val second = first.merge(lines("bravo line two", "charlie line three"))
        assertEquals("alpha line one\nbravo line two\ncharlie line three", second.text)
        assertEquals(first.lines, second.undo().lines)
    }

    @Test
    fun clearKeepsUndo() {
        val state = AccumulatorState().merge(lines("hello world"))
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
        val state = AccumulatorState().merge(lines("same line here"))
        assertSame(state, state.merge(lines("same line here")))
    }

    @Test
    fun undoOnEmptyHistoryIsNoop() {
        val state = AccumulatorState()
        assertSame(state, state.undo())
    }
}
