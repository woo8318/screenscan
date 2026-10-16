package com.woo.screenscan.ocr

import com.woo.screenscan.ocr.TestFixtures.lines
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameMergerTest {
    @Test
    fun appendsOnlyLinesPastTheOverlap() {
        val existing = lines("line1", "line2", "line3", "line4")
        val incoming = lines("line3", "line4", "line5", "line6")
        val expected = lines("line1", "line2", "line3", "line4", "line5", "line6")
        assertEquals(expected, FrameMerger.merge(existing, incoming))
    }

    @Test
    fun toleratesOcrNoiseInsideTheOverlap() {
        val existing = lines("logger.info(\"server started\")", "logger.info(\"listening on 8080\")", "return 0")
        val incoming = lines("logger.info(\"listening on 8O8O\")", "return O", "}")
        assertEquals(existing + lines("}"), FrameMerger.merge(existing, incoming))
    }

    @Test
    fun appendsEverythingWhenThereIsNoOverlap() {
        val existing = lines("alpha one", "beta two")
        val incoming = lines("gamma three", "delta four")
        assertEquals(existing + incoming, FrameMerger.merge(existing, incoming))
    }

    @Test
    fun addsNothingWhenFrameIsAlreadyCaptured() {
        val existing = lines("one two", "three four", "five six", "seven eight")
        val incoming = lines("three four", "five six")
        assertEquals(existing, FrameMerger.merge(existing, incoming))
    }

    @Test
    fun appliesIndentDeltaFromMatchedLines() {
        val existing = lines("fun main() {", "    val a = 1", "    val b = 2")
        val incoming = lines("val a = 1", "val b = 2", "    val c = 3", "}")
        val expected = lines("fun main() {", "    val a = 1", "    val b = 2", "        val c = 3", "    }")
        assertEquals(expected, FrameMerger.merge(existing, incoming))
    }

    @Test
    fun handlesRepeatedIdenticalLogLines() {
        val existing = lines("retrying connection", "retrying connection", "retrying connection", "connected")
        val incoming = lines("retrying connection", "connected", "ready to serve")
        assertEquals(existing + lines("ready to serve"), FrameMerger.merge(existing, incoming))
    }

    @Test
    fun ignoresBlankEdgesOfIncomingFrame() {
        val existing = lines("first line", "second line")
        val incoming = lines("", "second line", "third line", "")
        assertEquals(lines("first line", "second line", "third line"), FrameMerger.merge(existing, incoming))
    }

    @Test
    fun twoShortGenericLinesDoNotCountAsOverlap() {
        val existing = lines("a()", "}", "}")
        val incoming = lines("}", "}", "b()")
        assertEquals(existing + incoming, FrameMerger.merge(existing, incoming))
    }

    @Test
    fun singleShortCommonLineDoesNotCountAsOverlap() {
        val existing = lines("foo()", "}")
        val incoming = lines("}", "bar()")
        assertEquals(existing + incoming, FrameMerger.merge(existing, incoming))
    }
}
