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
    fun noisyRereadOfTheSameScreenAddsNothing() {
        val existing = lines(
            "전역 설정 kilo.jsonc 는 그대로 두셔도 됩니다.",
            "기본값과 같은 항목이라 전역 설정을 잠시 비활성화해도",
            "아직 정하지 않은 것 두 가지는 그대로입니다.",
            "새 설치 PC의 기본값은 다음 빌드에 반영하겠습니다.",
            "Thought for 1s",
            "Bash Read the result of the earlier session test",
        )
        val reread = lines(
            "전역 설정 kilo.jsonc 는 그대로 두셔도 됩니다.",
            "기본값과 같은 항목이라 진역 설정을 잠시 비활성화헤도",
            "아직 정하지 않은 것 두 가지는 그데로입니다.",
            "새 설치 PC의 기본값은 다음 빌드에 반영하겠습니다.",
            "Thougbt for 1s",
            "Bash Read the result of the earlier session test",
        )
        assertEquals(existing, merge(existing, reread, requireOverlap = true))
    }

    @Test
    fun garbledRereadIsNotAppendedBehindAShortTailMatch() {
        val existing = lines(
            "alpha line number one",
            "bravo line number two",
            "charlie line number three",
            "delta line number four",
            "echo line number five",
        )
        val reread = lines(
            "delta line number four",
            "echo line number five",
            "alpba lime numbor one",
            "bravo lune numbr twa",
            "charlie lina numder thre",
        )
        val result = FrameMerger.merge(existing, reread, requireOverlap = true)
        assertEquals(existing, result.lines)
        assertTrue(result is FrameMerger.Result.Unchanged)
    }

    @Test
    fun nearDuplicateIsHeldUntilASecondFrameConfirmsIt() {
        val existing = lines("alpha line one", "bravo line two", "retrying connection")
        val incoming = lines("bravo line two", "retrying connection", "retrying connection", "bravo lime twa")
        val first = FrameMerger.merge(existing, incoming, requireOverlap = true)
        // The exact repeat is appended at once; the misread look-alike is held back.
        assertEquals(existing + lines("retrying connection"), first.lines)
        assertEquals(listOf(LineNormalizer.normalize("bravo lime twa")), first.held)

        val second = FrameMerger.merge(first.lines, incoming, requireOverlap = true, confirmed = first.held.toSet())
        assertEquals(existing + lines("retrying connection", "bravo lime twa"), second.lines)
        assertTrue(second.held.isEmpty())
    }

    @Test
    fun fragmentsCutAtTheFrameEdgeMatchTheirFullLine() {
        val existing = lines(
            "Qwen3.8 모델 카드의 생각 모드 권장값은 temperature 1.0, top_p 0.95",
            "전역 설정은 0.6입니다",
        )
        val incoming = listOf(
            TextLine(0, "모델 카드의 생각 모드 권장값은 tempera", cutLeft = true, cutRight = true),
            TextLine(0, "설정은 0.6입니다", cutLeft = true),
            TextLine(0, "다음 줄입니다 새로운 내용"),
        )
        val merged = merge(existing, incoming, requireOverlap = true)
        assertEquals(existing + lines("다음 줄입니다 새로운 내용"), merged)
    }

    @Test
    fun stitchesLeftAndRightHalvesOfACutLine() {
        val existing = listOf(TextLine(2, "Qwen3.8 모델 카드의 생각 모드 권장값은 temperature", cutRight = true))
        val incoming = listOf(TextLine(0, "권장값은 temperature 1.0, top_p 0.95, top_k 20이고", cutLeft = true))
        val merged = merge(existing, incoming, requireOverlap = true)
        val expected = TextLine(2, "Qwen3.8 모델 카드의 생각 모드 권장값은 temperature 1.0, top_p 0.95, top_k 20이고")
        assertEquals(listOf(expected), merged)
    }

    @Test
    fun longerReadingReplacesAFragment() {
        val existing = listOf(TextLine(4, "설정은 0.6입니다", cutLeft = true))
        val incoming = listOf(TextLine(0, "전역 설정은 0.6입니다"))
        assertEquals(listOf(TextLine(4, "전역 설정은 0.6입니다")), merge(existing, incoming, requireOverlap = true))
    }

    @Test
    fun upgradesMatchedLinesWithMoreConfidentReading() {
        val existing = listOf(TextLine(4, "va1ue = c0mpute(x)", 0.6f), TextLine(4, "return value", 0.9f))
        val incoming = listOf(TextLine(0, "value = compute(x)", 0.95f), TextLine(0, "return value", 0.9f))
        val merged = merge(existing, incoming)
        assertEquals(listOf(TextLine(4, "value = compute(x)", 0.95f), TextLine(4, "return value", 0.9f)), merged)
    }
}
