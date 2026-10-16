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

/** Draws recognized line boxes over the preview: green when the frame is usable, red when rejected. */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val acceptedPaint = boxPaint(R.color.overlay_accepted)
    private val rejectedPaint = boxPaint(R.color.overlay_rejected)

    private var boxes: List<LineBox> = emptyList()
    private var accepted = true
    private var imageWidth = 1
    private var imageHeight = 1

    fun update(frame: Frame) {
        boxes = frame.boxes
        accepted = frame.quality.accepted
        imageWidth = max(1, frame.imageWidth)
        imageHeight = max(1, frame.imageHeight)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty()) return
        val paint = if (accepted) acceptedPaint else rejectedPaint
        val scale = max(width / imageWidth.toFloat(), height / imageHeight.toFloat())
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

    private fun boxPaint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        color = ContextCompat.getColor(context, colorRes)
    }

    private companion object {
        const val STROKE_WIDTH = 3f
    }
}
