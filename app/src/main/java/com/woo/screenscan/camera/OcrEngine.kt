package com.woo.screenscan.camera

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Owns the active ML Kit recognizer and swaps it when the language changes. */
class OcrEngine {
    private var language: OcrLanguage? = null
    private var recognizer: TextRecognizer? = null

    @Synchronized
    fun recognizer(wanted: OcrLanguage): TextRecognizer {
        val current = recognizer
        if (current != null && language == wanted) return current
        current?.close()
        val created = create(wanted)
        recognizer = created
        language = wanted
        return created
    }

    @Synchronized
    fun close() {
        recognizer?.close()
        recognizer = null
        language = null
    }

    private fun create(wanted: OcrLanguage): TextRecognizer = when (wanted) {
        OcrLanguage.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrLanguage.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
}
