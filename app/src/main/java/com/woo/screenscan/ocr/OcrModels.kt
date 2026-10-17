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
    /** Recognizer confidence 0..1; 1 when the engine reports none. */
    val confidence: Float = 1f,
    /** Rotation of the text baseline in degrees, clockwise positive. */
    val angle: Float = 0f,
)

/** A reconstructed text line: leading indentation in spaces plus the body. */
data class TextLine(
    val indent: Int,
    val body: String,
    val confidence: Float = 1f,
    /** The line ran off the left edge of the frame, so its start and indent are unknown. */
    val cutLeft: Boolean = false,
    /** The line ran off the right edge of the frame, so its end is unknown. */
    val cutRight: Boolean = false,
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

/** Why a frame was accepted or rejected before merging. */
enum class FrameVerdict {
    OK,
    EMPTY,
    BLURRY,
    TILTED,
}

data class FrameQuality(
    val verdict: FrameVerdict,
    val confidence: Float,
    val angle: Float,
) {
    val accepted: Boolean get() = verdict == FrameVerdict.OK
}

/** Result of one analyzed camera frame. */
data class Frame(
    val lines: List<TextLine>,
    val boxes: List<LineBox>,
    val imageWidth: Int,
    val imageHeight: Int,
    val quality: FrameQuality,
)

data class ExtractOptions(
    val stripLineNumbers: Boolean = false,
    /** Only lines whose centre lies inside this vertical band (fractions of image height) are used. */
    val bandTop: Float = 0f,
    val bandBottom: Float = 1f,
)
