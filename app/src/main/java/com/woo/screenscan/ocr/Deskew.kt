package com.woo.screenscan.ocr

import kotlin.math.cos
import kotlin.math.sin

/** Rotates recognizer geometry so that tilted text lines become horizontal before layout analysis. */
object Deskew {
    private const val MIN_ANGLE_TO_CORRECT = 0.5f

    private const val RIGHT_ANGLE = 90f
    private const val HALF_RIGHT_ANGLE = 45f

    fun medianAngle(lines: List<RawLine>): Float {
        if (lines.isEmpty()) return 0f
        val sorted = lines.map { foldAngle(it.angle) }.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * Folds an angle into (-45, 45]: text reported as ±90° or 180° is upright text described
     * in another orientation convention, not a tilt we should correct or reject.
     */
    fun foldAngle(angle: Float): Float {
        if (angle.isNaN()) return 0f
        var folded = angle % RIGHT_ANGLE
        if (folded > HALF_RIGHT_ANGLE) folded -= RIGHT_ANGLE
        if (folded <= -HALF_RIGHT_ANGLE) folded += RIGHT_ANGLE
        return folded + 0f // turns -0.0 into 0.0
    }

    /** Rotates every word box by -[angle] degrees around the image centre. */
    fun rotate(lines: List<RawLine>, angle: Float, imageWidth: Int, imageHeight: Int): List<RawLine> {
        if (kotlin.math.abs(angle) < MIN_ANGLE_TO_CORRECT) return lines
        val radians = Math.toRadians(-angle.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val cx = imageWidth / 2f
        val cy = imageHeight / 2f
        return lines.map { line ->
            if (line.words.isEmpty()) return@map line
            val words = line.words.map { rotateWord(it, cos, sin, cx, cy) }
            line.copy(
                words = words,
                left = words.minOf { it.left },
                top = words.minOf { it.top },
                right = words.maxOf { it.right },
                bottom = words.maxOf { it.bottom },
                angle = 0f,
            )
        }
    }

    private fun rotateWord(word: OcrWord, cos: Float, sin: Float, cx: Float, cy: Float): OcrWord {
        val corners = listOf(
            rotatePoint(word.left, word.top, cos, sin, cx, cy),
            rotatePoint(word.right, word.top, cos, sin, cx, cy),
            rotatePoint(word.left, word.bottom, cos, sin, cx, cy),
            rotatePoint(word.right, word.bottom, cos, sin, cx, cy),
        )
        return word.copy(
            left = corners.minOf { it.first },
            top = corners.minOf { it.second },
            right = corners.maxOf { it.first },
            bottom = corners.maxOf { it.second },
        )
    }

    private fun rotatePoint(x: Float, y: Float, cos: Float, sin: Float, cx: Float, cy: Float): Pair<Float, Float> {
        val dx = x - cx
        val dy = y - cy
        return (cx + dx * cos - dy * sin) to (cy + dx * sin + dy * cos)
    }
}
