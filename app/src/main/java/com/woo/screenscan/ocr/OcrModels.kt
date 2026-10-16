package com.woo.screenscan.ocr

/** One whitespace-separated token as reported by the recognizer, in image pixels. */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
}

/** One recognizer line (may be only part of a visual row when the engine splits columns). */
data class RawLine(
    val words: List<OcrWord>,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** A reconstructed text line: leading indentation in spaces plus the body. */
data class TextLine(
    val indent: Int,
    val body: String,
) {
    val text: String get() = " ".repeat(indent) + body
    val isBlank: Boolean get() = body.isBlank()

    companion object {
        val BLANK = TextLine(0, "")

        fun parse(raw: String): TextLine {
            val stripped = raw.trimStart(' ')
            return TextLine(raw.length - stripped.length, stripped.trimEnd())
        }
    }
}

/** Bounding box of a recognized row, in rotated-image pixels, for the camera overlay. */
data class LineBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Result of one analyzed camera frame. */
data class Frame(
    val lines: List<TextLine>,
    val boxes: List<LineBox>,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class ExtractOptions(
    val stripLineNumbers: Boolean = false,
)
