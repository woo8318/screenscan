package com.woo.screenscan.ocr

import com.woo.screenscan.ocr.TestFixtures.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMergerTest {
    private fun merge(existing: List<TextLine>, incoming: List<TextLine>, requireOverlap: Boolean = false) =
        FrameMerger.merge(existing, incoming, requireOverlap).lines

    @Test
    fun appendsOnlyLinesPastTheOverlap() {
        val existing = lines("line1", "line2", "line3", "line4")
        val incoming = lines("line3", "line4", "line5", "line6")
        val expected = lines("line1", "line2", "line3", "line4", "line5", "line6")
        assertEquals(expected, merge(existing, incoming))
    }

    @Test
    fun toleratesOcrNoiseInsideTheOverlap() {
        val existing = lines("logger.info(\"server started\")", "logger.info(\"listening on 8080\")", "return 0")
        val incoming = lines("logger.info(\"listening on 8O8O\")", "return O", "}")
        assertEquals(existing + lines("}"), merge(existing, incoming))
    }

    @Test
    fun longLinesAcceptMoreNoise() {
        val existing = lines("2026-10-17 12:00:01 INFO worker-3 connection established to db-primary")
        // 7 real substitutions over 63 normalized characters: below 0.9 but above the long-line 0.85.
        val incoming = lines("2026-10-17 12:00:01 INFO wurker-3 cannectian estoblishad tu db-primery", "next line here")
        assertEquals(existing + lines("next line here"), merge(existing, incoming))
    }

    @Test
    fun appendsEverythingWhenThereIsNoOverlapAndNotRequired() {
        val existing = lines("alpha one", "beta two")
        val incoming = lines("gamma three", "delta four")
        assertEquals(existing + incoming, merge(existing, incoming))
    }

    @Test
    fun ignoresFrameWithoutOverlapWhenRequired() {
        val existing = lines("alpha one", "beta two")
        val incoming = lines("gamma three", "delta four")
        val result = FrameMerger.merge(existing, incoming, requireOverlap = true)
        assertTrue(result is FrameMerger.Result.NoOverlap)
        assertEquals(existing, result.lines)
    }

    @Test
    fun firstFrameIsAcceptedEvenWhenOverlapIsRequired() {
        val incoming = lines("first line", "second line")
        assertEquals(incoming, merge(emptyList(), incoming, requireOverlap = true))
    }

    @Test
    fun addsNothingWhenFrameIsAlreadyCaptured() {
        val existing = lines("one two", "three four", "five six", "seven eight")
        val incoming = lines("three four", "five six")
        assertEquals(existing, merge(existing, incoming))
    }

    @Test
    fun appliesIndentDeltaFromMatchedLines() {
        val existing = lines("fun main() {", "    val a = 1", "    val b = 2")
        val incoming = lines("val a = 1", "val b = 2", "    val c = 3", "}")
        val expected = lines("fun main() {", "    val a = 1", "    val b = 2", "        val c = 3", "    }")
        assertEquals(expected, merge(existing, incoming))
    }

    @Test
    fun handlesRepeatedIdenticalLogLines() {
        val existing = lines("retrying connection", "retrying connection", "retrying connection", "connected")
        val incoming = lines("retrying connection", "connected", "ready to serve")
        assertEquals(existing + lines("ready to serve"), merge(existing, incoming))
    }

    @Test
    fun ignoresBlankEdgesOfIncomingFrame() {
        val existing = lines("first line", "second line")
        val incoming = lines("", "second line", "third line", "")
        assertEquals(lines("first line", "second line", "third line"), merge(existing, incoming))
    }

    @Test
    fun twoShortGenericLinesDoNotCountAsOverlap() {
        val existing = lines("a()", "}", "}")
        val incoming = lines("}", "}", "b()")
        assertEquals(existing + incoming, merge(existing, incoming))
    }

    @Test
    fun singleShortCommonLineDoesNotCountAsOverlap() {
        val existing = lines("foo()", "}")
        val incoming = lines("}", "bar()")
        assertEquals(existing + incoming, merge(existing, incoming))
    }

    @Test
    fun upgradesMatchedLinesWithMoreConfidentReading() {
        val existing = listOf(TextLine(4, "va1ue = c0mpute(x)", 0.6f), TextLine(4, "return value", 0.9f))
        val incoming = listOf(TextLine(0, "value = compute(x)", 0.95f), TextLine(0, "return value", 0.9f))
        val merged = merge(existing, incoming)
        assertEquals(listOf(TextLine(4, "value = compute(x)", 0.95f), TextLine(4, "return value", 0.9f)), merged)
    }
}
