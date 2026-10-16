package com.woo.screenscan.camera

import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/** Binds preview and analysis use cases to the lifecycle and exposes zoom and focus controls. */
class CameraBinder(
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analysisExecutor: Executor,
) {
    private var camera: Camera? = null

    fun start(analyzer: ImageAnalysis.Analyzer) {
        val context = previewView.context
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ bind(future.get(), analyzer) }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(provider: ProcessCameraProvider, analyzer: ImageAnalysis.Analyzer) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val aspect = AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val analysisSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspect)
            .setResolutionStrategy(
                ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor, analyzer)

        provider.unbindAll()
        camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }

    fun zoomBy(factor: Float) {
        val cam = camera ?: return
        val zoom = cam.cameraInfo.zoomState.value ?: return
        val target = (zoom.zoomRatio * factor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
        cam.cameraControl.setZoomRatio(target)
    }

    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    private companion object {
        val ANALYSIS_SIZE = Size(1920, 1080)
    }
}
