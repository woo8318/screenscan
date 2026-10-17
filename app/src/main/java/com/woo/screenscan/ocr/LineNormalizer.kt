package com.woo.screenscan.ocr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Text normalization and fuzzy comparison used to align frames despite OCR noise. */
object LineNormalizer {
    private const val MAX_LENGTH_DIFF_RATIO = 0.4f
    const val EDGE_SLACK = 2

    private val confusables = mapOf(
        'o' to '0',
        'l' to '1',
        'i' to '1',
        '|' to '1',
        'ㅣ' to '1', // Hangul letter I (ㅣ), often returned for a vertical bar
    )

    /** Lowercase, whitespace removed, common OCR confusions folded. */
    fun normalize(text: String): String = buildString(text.length) {
        for (ch in text) {
            if (ch.isWhitespace()) continue
            val lower = ch.lowercaseChar()
            append(confusables[lower] ?: lower)
        }
    }

    /** 1.0 for identical strings, 0.0 for unrelated ones. Expects normalized input. */
    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1f
        if (abs(a.length - b.length) > maxLen * MAX_LENGTH_DIFF_RATIO) return 0f
        val limit = ceil(maxLen * MAX_LENGTH_DIFF_RATIO).toInt()
        val distance = levenshtein(a, b, limit)
        return if (distance > limit) 0f else 1f - distance.toFloat() / maxLen
    }

    /**
     * Similarity that lets the shorter string sit anywhere inside the longer one, for lines
     * that were cut off at the frame edge. 1.0 when the short string is a substring.
     */
    fun containmentSimilarity(a: String, b: String): Float {
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (short.isEmpty()) return 0f
        if (long.contains(short)) return 1f
        val distance = semiGlobalDistance(short, long)
        return (1f - distance.toFloat() / short.length).coerceAtLeast(0f)
    }

    /**
     * Longest run where the end of one string equals the start of the other, i.e. the two are
     * the left and right halves of a line that was cut in a frame. Up to [EDGE_SLACK]
     * garbled characters at the cut edges are ignored. The whole string never counts.
     */
    fun edgeOverlap(a: String, b: String): Int {
        if (a == b) return 0
        return max(suffixPrefixOverlap(a, b), suffixPrefixOverlap(b, a))
    }

    private fun suffixPrefixOverlap(left: String, right: String): Int {
        var best = 0
        for (leftDrop in 0..EDGE_SLACK) {
            for (rightDrop in 0..EDGE_SLACK) {
                val l = left.dropLast(leftDrop)
                val r = right.drop(rightDrop)
                val longest = min(l.length, r.length) - 1
                for (k in longest downTo best + 1) {
                    if (l.regionMatches(l.length - k, r, 0, k)) {
                        best = k
                        break
                    }
                }
            }
        }
        return best
    }

    /** Minimum edits to turn [pattern] into some substring of [text]. */
    fun semiGlobalDistance(pattern: String, text: String): Int {
        if (pattern.isEmpty()) return 0
        if (text.isEmpty()) return pattern.length
        var previous = IntArray(text.length + 1) // free start anywhere in text
        var current = IntArray(text.length + 1)
        for (i in 1..pattern.length) {
            current[0] = i
            for (j in 1..text.length) {
                val cost = if (pattern[i - 1] == text[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous.min()
    }

    /** Levenshtein distance; returns limit + 1 as soon as the distance is known to exceed [limit]. */
    fun levenshtein(a: String, b: String, limit: Int): Int {
        if (a.isEmpty()) return min(b.length, limit + 1)
        if (b.isEmpty()) return min(a.length, limit + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return min(previous[b.length], limit + 1)
    }
}
