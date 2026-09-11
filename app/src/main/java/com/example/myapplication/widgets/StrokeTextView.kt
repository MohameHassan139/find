package com.example.myapplication.widgets

import android.content.Context
import android.content.res.Configuration
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
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            // Light: original soft shadow. Dark design: text-shadow 0 4px 4px rgba(0,0,0,0.25)
            val shadowRadius = if (isNight) 2.6f * density else 3f * density
            val shadowDx = 0f
            val shadowDy = if (isNight) 4f * density else 1.5f * density
            val shadowColor = if (isNight) 0x40000000 else 0x59000000
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
