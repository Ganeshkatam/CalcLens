package com.calclens.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.View
import com.calclens.tracking.ImuMotionPredictor
import com.calclens.tracking.SpatialTracker
import com.calclens.tracking.TrackingStatus

/**
 * Low-latency, hardware-accelerated overlay View that renders sticky mathematical
 * badges at the display's native refresh rate (up to 120 Hz) without Compose recomposition overhead.
 */
class StickyOverlayView(
    context: Context,
    private val tracker: SpatialTracker,
    private val imuPredictor: ImuMotionPredictor,
    private val transformer: CoordinateTransformer
) : View(context), Choreographer.FrameCallback {

    private var isRunning = false

    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(100, 96, 165, 250) // AccentBlue semi-transparent
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 15, 23, 42) // Frosted glass dark slate
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.argb(255, 34, 197, 94) // AccentGreen
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f // Sharp, readable mono digits
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val errorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.argb(255, 239, 68, 68) // AccentRed
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            transformer.viewportWidth = w.toFloat()
            transformer.viewportHeight = h.toFloat()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        imuPredictor.start()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        imuPredictor.stop()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        // 1. Consume high-frequency gyroscope delta and apply directly to tracking anchors
        val (dx, dy) = imuPredictor.consumeDelta()
        if (dx != 0f || dy != 0f) {
            tracker.applyImuMotion(dx, dy)
        }

        // 2. Request immediate redraw on hardware-accelerated render thread
        invalidate()

        // 3. Re-queue for next display vsync (8.33ms at 120Hz)
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val activeEntities = tracker.getEntities().filter {
            it.status == TrackingStatus.DISPLAYING && (it.result != null || it.errorMessage != null)
        }

        val textBounds = android.graphics.Rect()

        for (entity in activeEntities) {
            val exprBounds = transformer.toScreenRect(entity.smoothedBox)
            val textToDisplay = entity.result ?: entity.errorMessage ?: ""
            val badgeBounds = transformer.computeBadgePlacement(
                expressionRect = exprBounds,
                textLength = textToDisplay.length
            )

            // 1. Draw dashed/clean reticle around physical equation
            canvas.drawRoundRect(
                exprBounds.left,
                exprBounds.top,
                exprBounds.right,
                exprBounds.bottom,
                12f,
                12f,
                reticlePaint
            )

            // 2. Draw anchored result badge
            val border = if (entity.errorMessage != null) errorBorderPaint else badgeBorderPaint
            canvas.drawRoundRect(
                badgeBounds.left,
                badgeBounds.top,
                badgeBounds.right,
                badgeBounds.bottom,
                16f,
                16f,
                badgeBgPaint
            )
            canvas.drawRoundRect(
                badgeBounds.left,
                badgeBounds.top,
                badgeBounds.right,
                badgeBounds.bottom,
                16f,
                16f,
                border
            )

            // 3. Draw result text vertically centered in badge with dynamic scale
            val dynamicTextSize = (badgeBounds.height * 0.65f).coerceIn(24f, 44f)
            textPaint.textSize = dynamicTextSize
            textPaint.getTextBounds(textToDisplay, 0, textToDisplay.length, textBounds)
            val textY = badgeBounds.centerY + (textBounds.height() * 0.35f)
            canvas.drawText(textToDisplay, badgeBounds.centerX, textY, textPaint)
        }
    }
}
