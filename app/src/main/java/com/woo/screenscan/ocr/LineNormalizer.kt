package com.woo.screenscan.ocr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Text normalization and fuzzy comparison used to align frames despite OCR noise. */
object LineNormalizer {
    private const val MAX_LENGTH_DIFF_RATIO = 0.4f

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
