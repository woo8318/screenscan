package com.woo.screenscan.ocr

import kotlin.math.abs

/** Decides whether two consecutive frames show the same scene (phone held still). */
object FrameStability {
    private const val MIN_EQUAL_RATIO = 0.8f
    private const val MAX_LINE_COUNT_DIFF = 1

    fun key(frame: Frame): List<String> =
        frame.lines.filterNot { it.isBlank }.map { LineNormalizer.normalize(it.body) }

    fun isSame(previous: List<String>?, current: List<String>): Boolean {
        if (previous == null) return false
        if (previous.isEmpty() || current.isEmpty()) return previous.isEmpty() && current.isEmpty()
        if (abs(previous.size - current.size) > MAX_LINE_COUNT_DIFF) return false
        val equal = previous.zip(current).count { (a, b) -> a == b }
        return equal.toFloat() / maxOf(previous.size, current.size) >= MIN_EQUAL_RATIO
    }
}
