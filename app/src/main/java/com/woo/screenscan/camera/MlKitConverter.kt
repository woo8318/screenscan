package com.woo.screenscan.camera

import com.google.mlkit.vision.text.Text
import com.woo.screenscan.ocr.OcrWord
import com.woo.screenscan.ocr.RawLine

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
            angle = line.angle ?: 0f,
        )
    }

    /** Some models report no confidence (0 or NaN); treat that as "unknown" rather than "bad". */
    private fun knownConfidence(value: Float?): Float =
        if (value == null || value.isNaN() || value <= 0f) 1f else value
}
