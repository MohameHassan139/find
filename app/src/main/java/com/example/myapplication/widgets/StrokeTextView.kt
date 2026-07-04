package com.example.myapplication.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var isStrokeEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var strokeColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var strokeWidth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        // Calculate a reasonable default stroke width based on screen density (e.g. 2dp)
        val density = resources.displayMetrics.density
        strokeWidth = 2f * density
    }

    override fun onDraw(canvas: Canvas) {
        if (isStrokeEnabled) {
            val originalColor = textColors

            // 1. Draw outline with shadow
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            setTextColor(strokeColor)
            
            val density = resources.displayMetrics.density
            val shadowRadius = 3f * density
            val shadowDx = 0f
            val shadowDy = 1.5f * density
            val shadowColor = 0x59000000 // 35% black shadow
            setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
            
            super.onDraw(canvas)

            // 2. Draw fill (without shadow)
            paint.style = Paint.Style.FILL
            setTextColor(originalColor)
            setShadowLayer(0f, 0f, 0f, 0)
            super.onDraw(canvas)
        } else {
            setShadowLayer(0f, 0f, 0f, 0)
            super.onDraw(canvas)
        }
    }
}
