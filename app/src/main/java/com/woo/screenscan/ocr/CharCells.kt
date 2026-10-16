package com.woo.screenscan.ocr

/** Monospace cell counting: East Asian wide characters (Hangul, CJK, full-width) take two cells. */
object CharCells {
    private val wideRanges = listOf(
        0x1100..0x115F, // Hangul Jamo
        0x2E80..0x303E, // CJK radicals, punctuation
        0x3041..0x33FF, // Kana, Hangul compatibility Jamo, enclosed CJK
        0x3400..0x4DBF, // CJK extension A
        0x4E00..0x9FFF, // CJK unified ideographs
        0xA960..0xA97F, // Hangul Jamo extended-A
        0xAC00..0xD7A3, // Hangul syllables
        0xF900..0xFAFF, // CJK compatibility ideographs
        0xFE30..0xFE4F, // CJK compatibility forms
        0xFF00..0xFF60, // Full-width forms
        0xFFE0..0xFFE6, // Full-width signs
    )

    fun isWide(ch: Char): Boolean = wideRanges.any { ch.code in it }

    fun count(text: String): Int = text.length + text.count(::isWide)
}
