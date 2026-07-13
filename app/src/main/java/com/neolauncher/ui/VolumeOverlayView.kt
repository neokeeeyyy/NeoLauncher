package com.neolauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.view.MotionEvent
import android.view.View

class VolumeOverlayView(context: Context) : View(context) {

    private var volumePercent = 0f
    private var activeStream = AudioManager.STREAM_MUSIC
    private var streamChangedCallback: ((Int) -> Unit)? = null
    private var dismissCallback: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(68, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private var cx = 0f
    private var cy = 0f
    private val arcRadius = 240f
    private val strokeWidth = 36f

    private val startAngle = -220f
    private val sweepAngle = 260f

    private var touchStartX = 0f

    private val streamNames = mapOf(
        AudioManager.STREAM_MUSIC to "Media",
        AudioManager.STREAM_RING to "Llamada",
        AudioManager.STREAM_ALARM to "Alarma",
        AudioManager.STREAM_NOTIFICATION to "Notif.",
        AudioManager.STREAM_SYSTEM to "Sistema"
    )

    init {
        bgPaint.strokeWidth = strokeWidth
        fgPaint.strokeWidth = strokeWidth
        hintPaint.textSize = 30f
    }

    fun setVolume(pct: Float) {
        volumePercent = pct.coerceIn(0f, 1f)
        invalidate()
    }

    fun setStream(stream: Int) {
        activeStream = stream
        invalidate()
    }

    fun setStreamChangedCallback(cb: (Int) -> Unit) {
        streamChangedCallback = cb
    }

    fun setDismissCallback(cb: () -> Unit) {
        dismissCallback = cb
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2.5f
        arcRect.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.argb(200, 0, 0, 0))

        canvas.drawArc(arcRect, startAngle, sweepAngle, false, bgPaint)

        val fillSweep = sweepAngle * volumePercent
        canvas.drawArc(arcRect, startAngle, fillSweep, false, fgPaint)

        val name = streamNames[activeStream] ?: "Media"
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 48f
        }
        canvas.drawText(name, cx, cy - 20f, namePaint)

        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(179, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 72f
        }
        canvas.drawText("${(volumePercent * 100).toInt()}%", cx, cy + 60f, pctPaint)

        canvas.drawText("\u2039 desliza \u203a", cx, height - 80f, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                if (kotlin.math.abs(dx) > 80) {
                    streamChangedCallback?.invoke(if (dx < 0) 1 else -1)
                } else {
                    dismissCallback?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                dismissCallback?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
