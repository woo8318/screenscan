package com.woo.screenscan.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.woo.screenscan.R
import com.woo.screenscan.ocr.Frame
import com.woo.screenscan.ocr.LineBox
import kotlin.math.max

/**
 * Draws the scan band and recognized line boxes over the preview, assuming the preview
 * fills and centre-crops. Boxes are green when the frame is usable, red when rejected.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Band expressed as fractions of the image height, reported whenever it changes. */
    fun interface BandListener {
        fun onImageBand(top: Float, bottom: Float)
    }

    private val acceptedPaint = boxPaint(R.color.overlay_accepted)
    private val rejectedPaint = boxPaint(R.color.overlay_rejected)
    private val dimPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.overlay_dim) }
    private val bandPaint = boxPaint(R.color.overlay_band).apply { strokeWidth = BAND_STROKE_WIDTH }

    private var boxes: List<LineBox> = emptyList()
    private var accepted = true
    private var imageWidth = 1
    private var imageHeight = 1
    private var bandFraction = 1f
    private var reportedBand: Pair<Float, Float>? = null
    var bandListener: BandListener? = null

    fun update(frame: Frame) {
        boxes = frame.boxes
        accepted = frame.quality.accepted
        imageWidth = max(1, frame.imageWidth)
        imageHeight = max(1, frame.imageHeight)
        reportBand()
        invalidate()
    }

    /** Sets the band height as a fraction of the visible preview, centred vertically. */
    fun setBandFraction(fraction: Float) {
        bandFraction = fraction.coerceIn(MIN_BAND, 1f)
        reportBand()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reportBand()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBand(canvas)
        if (boxes.isEmpty()) return
        val paint = if (accepted) acceptedPaint else rejectedPaint
        val scale = scale()
        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f
        boxes.forEach { box ->
            canvas.drawRect(
                box.left * scale + dx,
                box.top * scale + dy,
                box.right * scale + dx,
                box.bottom * scale + dy,
                paint,
            )
        }
    }

    private fun drawBand(canvas: Canvas) {
        if (bandFraction >= 1f || height == 0) return
        val (top, bottom) = viewBand()
        canvas.drawRect(0f, 0f, width.toFloat(), top, dimPaint)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, top, width.toFloat(), bottom, bandPaint)
    }

    private fun viewBand(): Pair<Float, Float> {
        val half = height * bandFraction / 2f
        val center = height / 2f
        return (center - half) to (center + half)
    }

    private fun scale(): Float = max(width / imageWidth.toFloat(), height / imageHeight.toFloat())

    /** Converts the view band into image-height fractions and notifies the listener on change. */
    private fun reportBand() {
        if (width == 0 || height == 0) return
        val scale = scale()
        val dy = (height - imageHeight * scale) / 2f
        val (top, bottom) = viewBand()
        val imageTop = ((top - dy) / scale / imageHeight).coerceIn(0f, 1f)
        val imageBottom = ((bottom - dy) / scale / imageHeight).coerceIn(0f, 1f)
        val band = imageTop to imageBottom
        if (band != reportedBand) {
            reportedBand = band
            bandListener?.onImageBand(imageTop, imageBottom)
        }
    }

    private fun boxPaint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        color = ContextCompat.getColor(context, colorRes)
    }

    private companion object {
        const val STROKE_WIDTH = 3f
        const val BAND_STROKE_WIDTH = 4f
        const val MIN_BAND = 0.15f
    }
}
