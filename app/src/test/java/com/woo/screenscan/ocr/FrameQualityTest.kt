package com.woo.screenscan.ocr

import com.woo.screenscan.ocr.TestFixtures.rawLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FrameQualityTest {
    private val width = 1920
    private val height = 1080

    @Test
    fun emptyFrameIsReportedEmpty() {
        val frame = FrameExtractor.extract(emptyList(), width, height)
        assertEquals(FrameVerdict.EMPTY, frame.quality.verdict)
        assertTrue(frame.lines.isEmpty())
    }

    @Test
    fun blurryFrameIsRejectedButStillRendered() {
        val raw = listOf(
            rawLine(0, 0 to "garbage").copy(confidence = 0.3f),
            rawLine(1, 0 to "noise").copy(confidence = 0.4f),
            rawLine(2, 0 to "clear").copy(confidence = 0.9f),
        )
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(FrameVerdict.BLURRY, frame.quality.verdict)
        assertEquals(listOf("clear"), frame.lines.map { it.text })
    }

    @Test
    fun lowConfidenceLinesAreDroppedFromAcceptedFrames() {
        val raw = listOf(
            rawLine(0, 0 to "good line"),
            rawLine(1, 0 to "smudge").copy(confidence = 0.2f),
            rawLine(2, 0 to "another good"),
            rawLine(3, 0 to "third good"),
            rawLine(4, 0 to "fourth good"),
        )
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(FrameVerdict.OK, frame.quality.verdict)
        val expected = listOf("good line", "", "another good", "third good", "fourth good")
        assertEquals(expected, frame.lines.map { it.text })
    }

    @Test
    fun onlyLinesInsideTheBandAreUsed() {
        val raw = listOf(
            rawLine(0, 0 to "above band"),   // centre y = 110
            rawLine(10, 0 to "inside one"),  // centre y = 410
            rawLine(11, 0 to "inside two"),  // centre y = 440
            rawLine(30, 0 to "below band"),  // centre y = 1010
        )
        val options = ExtractOptions(bandTop = 0.3f, bandBottom = 0.5f)
        val frame = FrameExtractor.extract(raw, width, height, options)
        assertEquals(listOf("inside one", "inside two"), frame.lines.map { it.text })
    }

    @Test
    fun stronglyTiltedFrameIsRejected() {
        val raw = listOf(rawLine(0, 0 to "a b").copy(angle = 20f), rawLine(1, 0 to "c d").copy(angle = 18f))
        val frame = FrameExtractor.extract(raw, width, height)
        assertEquals(FrameVerdict.TILTED, frame.quality.verdict)
    }

    @Test
    fun deskewRestoresHorizontalRows() {
        val original = listOf(rawLine(0, 0 to "hello", 8 to "world"), rawLine(1, 0 to "second"))
        val tilted = Deskew.rotate(original, -5f, width, height).map { it.copy(angle = 5f) }
        val restored = Deskew.rotate(tilted, Deskew.medianAngle(tilted), width, height)
        // Rotating axis-aligned boxes inflates them slightly, so allow a few pixels of drift.
        original.zip(restored).forEach { (a, b) ->
            assertTrue(abs(a.top - b.top) < 8f)
            assertTrue(abs(a.left - b.left) < 8f)
        }
        val frame = FrameExtractor.extract(tilted, width, height)
        assertEquals(FrameVerdict.OK, frame.quality.verdict)
        assertEquals(2, frame.lines.size)
        assertTrue(frame.lines[0].text.startsWith("hello") && frame.lines[0].text.endsWith("world"))
        assertEquals("second", frame.lines[1].text)
    }
}
