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
        return RawLine(words, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
    }
}
