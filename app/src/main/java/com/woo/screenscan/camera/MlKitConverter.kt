package com.woo.screenscan.camera

import android.graphics.Point
import com.google.mlkit.vision.text.Text
import com.woo.screenscan.ocr.Deskew
import com.woo.screenscan.ocr.OcrWord
import com.woo.screenscan.ocr.RawLine
import kotlin.math.atan2

/** Converts ML Kit output into the engine-agnostic model used by the extractor. */
object MlKitConverter {
    fun toRawLines(text: Text): List<RawLine> =
        text.textBlocks.flatMap { block -> block.lines }.mapNotNull(::toRawLine)

    private fun toRawLine(line: Text.Line): RawLine? {
        val box = line.boundingBox ?: return null
        val words = line.elements.mapNotNull { element ->
            element.boundingBox?.let { b ->
                OcrWord(element.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
            }
        }
        if (words.isEmpty()) return null
        return RawLine(
            words = words,
            left = box.left.toFloat(),
            top = box.top.toFloat(),
            right = box.right.toFloat(),
            bottom = box.bottom.toFloat(),
            confidence = knownConfidence(line.confidence),
            angle = lineAngle(line),
        )
    }

    /** Some models report no confidence (0 or NaN); treat that as "unknown" rather than "bad". */
    private fun knownConfidence(value: Float?): Float =
        if (value == null || value.isNaN() || value <= 0f) 1f else value

    /**
     * Tilt of the text baseline in the same coordinate system as the bounding boxes.
     * ML Kit's own angle is relative to the sensor orientation (upright text reads as -90°),
     * so the top edge of the corner quadrilateral is measured instead when available.
     */
    private fun lineAngle(line: Text.Line): Float {
        val corners = line.cornerPoints
        val fromCorners = if (corners != null && corners.size >= 2) angleBetween(corners[0], corners[1]) else null
        val raw = fromCorners ?: line.angle ?: 0f
        return Deskew.foldAngle(raw)
    }

    private fun angleBetween(a: Point, b: Point): Float? {
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        if (dx == 0f && dy == 0f) return null
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }
}
