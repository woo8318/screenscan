package com.woo.screenscan.camera

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.woo.screenscan.ocr.ExtractOptions
import com.woo.screenscan.ocr.Frame
import com.woo.screenscan.ocr.FrameExtractor
import java.util.concurrent.Executor

/** Runs ML Kit on each camera frame and hands the extracted lines to [onFrame] on [executor]. */
class TextAnalyzer(
    private val executor: Executor,
    private val recognizer: () -> TextRecognizer,
    private val options: () -> ExtractOptions,
    private val onFrame: (Frame) -> Unit,
) : ImageAnalysis.Analyzer {

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val sideways = rotation == 90 || rotation == 270
        val width = if (sideways) imageProxy.height else imageProxy.width
        val height = if (sideways) imageProxy.width else imageProxy.height
        val extractOptions = options()

        try {
            recognizer().process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener(executor) { text ->
                    onFrame(FrameExtractor.extract(MlKitConverter.toRawLines(text), width, height, extractOptions))
                }
                .addOnFailureListener(executor) { error -> Log.w(TAG, "Text recognition failed", error) }
                .addOnCompleteListener(DIRECT_EXECUTOR) { imageProxy.close() }
        } catch (error: Exception) {
            Log.w(TAG, "Could not start recognition", error)
            imageProxy.close()
        }
    }

    private companion object {
        const val TAG = "TextAnalyzer"

        /** Closing the frame must not depend on an executor that may already be shut down. */
        val DIRECT_EXECUTOR = Executor { runnable -> runnable.run() }
    }
}
