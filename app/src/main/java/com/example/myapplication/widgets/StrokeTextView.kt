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
        super.onDraw(canvas)
    }
}
