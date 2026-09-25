package com.example.myapplication.widgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.example.myapplication.FindFonts
import com.example.myapplication.R

class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var isStrokeEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var strokeColor: Int = Color.BLACK
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var strokeWidthPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var isDrawing = false

    init {
        val density = resources.displayMetrics.density
        strokeWidthPx = 2f * density
    }

    override fun invalidate() {
        if (!isDrawing) {
            super.invalidate()
        }
    }

    override fun requestLayout() {
        if (!isDrawing) {
            super.requestLayout()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (isStrokeEnabled && strokeWidthPx > 0) {
            isDrawing = true
            val originalColors = textColors
            val originalStyle = paint.style
            val originalStrokeWidth = paint.strokeWidth
            val originalStrokeJoin = paint.strokeJoin
            val originalStrokeCap = paint.strokeCap

            try {
                // 1. Draw outline stroke behind text
                paint.style = Paint.Style.STROKE
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = strokeWidthPx
                setTextColor(strokeColor)
                super.onDraw(canvas)

                // 2. Draw interior fill on top
                paint.style = Paint.Style.FILL
                setTextColor(originalColors)
                super.onDraw(canvas)
            } finally {
                paint.style = originalStyle
                paint.strokeWidth = originalStrokeWidth
                paint.strokeJoin = originalStrokeJoin
                paint.strokeCap = originalStrokeCap
                setTextColor(originalColors)
                isDrawing = false
            }
        } else {
            super.onDraw(canvas)
        }
    }

    /**
     * Applies the tab active/inactive styling:
     * - In Light Mode: Selected text is white with a black outline border ("النص المحدد يكون أبيض و border أسود").
     * - In Dark Mode: Selected text is bold black (for the dark mode yellow strip).
     * - Inactive text: Uses tab_label_inactive with normal typeface.
     */
    fun applyTabState(isActive: Boolean) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val tf = FindFonts.typeface(context)
        if (isActive) {
            setTypeface(tf, Typeface.BOLD)
            if (!isDark) {
                isStrokeEnabled = true
                strokeColor = Color.BLACK
                setTextColor(Color.WHITE)
            } else {
                isStrokeEnabled = false
                setTextColor(ContextCompat.getColor(context, R.color.tab_label_active))
            }
        } else {
            isStrokeEnabled = false
            setTypeface(tf, Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(context, R.color.tab_label_inactive))
        }
    }
}
