package com.woo.screenscan.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineNormalizerTest {
    @Test
    fun normalizeStripsWhitespaceAndFoldsConfusables() {
        assertEquals("va1x=0", LineNormalizer.normalize("  val x = O "))
    }

    @Test
    fun identicalStringsScoreOne() {
        assertEquals(1f, LineNormalizer.similarity("abc", "abc"))
    }

    @Test
    fun oneCharNoiseStaysAboveThreshold() {
        val score = LineNormalizer.similarity("loggerinfo(started)", "loggerinfo(startrd)")
        assertTrue(score > 0.9f)
    }

    @Test
    fun unrelatedStringsScoreZero() {
        assertEquals(0f, LineNormalizer.similarity("abc", "abcdefghijkl"))
    }

    @Test
    fun containmentFindsFragmentsInsideLongerLines() {
        assertEquals(1f, LineNormalizer.containmentSimilarity("abcdef", "xxabcdefyy"))
        assertEquals(1, LineNormalizer.semiGlobalDistance("abcdef", "xxabXdefyy"))
        assertTrue(LineNormalizer.containmentSimilarity("abcdef", "zzzzzzzzzz") < 0.5f)
    }

    @Test
    fun edgeOverlapIgnoresGarbledCutCharacters() {
        assertEquals(8, LineNormalizer.edgeOverlap("startabcdefgh", "abcdefghend"))
        assertEquals(8, LineNormalizer.edgeOverlap("startabcdefghX", "YabcdefghEnd"))
        assertEquals(0, LineNormalizer.edgeOverlap("abcdefgh", "abcdefgh"))
    }

    @Test
    fun levenshteinStopsAtLimit() {
        assertEquals(3, LineNormalizer.levenshtein("kitten", "sitting", 10))
        assertEquals(2, LineNormalizer.levenshtein("kitten", "sitting", 1))
    }
}
