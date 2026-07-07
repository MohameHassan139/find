package com.example.myapplication.chat.ui.chat

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Custom Kotlin Drawable for speech bubbles with custom corner radii and pointed triangular tail.
 */
class BubbleDrawable(
    private val fillColor: Int,
    private val strokeColor: Int = 0,
    private val strokeWidth: Float = 0f,
    private val cornerRadius: Float = 36f,
    private val tailWidth: Float = 20f,
    private val tailHeight: Float = 20f,
    private val isTailOnLeft: Boolean = true
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }

    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        path.reset()

        val inset = strokeWidth / 2f
        val left = b.left.toFloat() + inset + if (isTailOnLeft) tailWidth else 0f
        val top = b.top.toFloat() + inset
        val right = b.right.toFloat() - inset - if (!isTailOnLeft) tailWidth else 0f
        val bottom = b.bottom.toFloat() - inset - tailHeight / 2f

        val bodyRect = RectF(left, top, right, bottom)
        path.addRoundRect(bodyRect, cornerRadius, cornerRadius, Path.Direction.CW)

        if (isTailOnLeft) {
            val tailPath = Path().apply {
                moveTo(left + cornerRadius, bottom)
                lineTo(b.left.toFloat() + inset, b.bottom.toFloat() - inset)
                lineTo(left, bottom - cornerRadius)
                close()
            }
            path.op(tailPath, Path.Op.UNION)
        } else {
            val tailPath = Path().apply {
                moveTo(right - cornerRadius, bottom)
                lineTo(b.right.toFloat() - inset, b.bottom.toFloat() - inset)
                lineTo(right, bottom - cornerRadius)
                close()
            }
            path.op(tailPath, Path.Op.UNION)
        }

        canvas.drawPath(path, fillPaint)
        if (strokeWidth > 0f) {
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
