package com.example.myapplication

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView

/**
 * DEBUG ONLY — shows every Find text style (Compose + XML) with the two test
 * sentences, and logs which font file draws the Arabic letters and the digits.
 *
 *   adb shell am start -n com.finds.app/com.example.myapplication.FontDebugActivity
 *   adb logcat -s FindFonts
 */
class FontDebugActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        FindTypefaceInflater.install(this)
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent { FindTheme { FindTypeSpecimen() } }
        })
        logGlyphSources()
    }

    /** Proves the per-glyph fallback: Arabic mode uses Noto Sans Arabic primary; English mode uses Inter primary. */
    @android.annotation.SuppressLint("NewApi")
    private fun logGlyphSources() {
        val isAr = com.example.myapplication.utils.LocaleHelper.isArabic(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val fontName = if (isAr) "Noto Sans Arabic" else "Inter"
            Log.i(TAG, "API ${Build.VERSION.SDK_INT} < 29: whole text uses $fontName (expected)")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.i(TAG, "API ${Build.VERSION.SDK_INT}: per-glyph fallback active; glyph-source logging needs API 31+")
            return
        }
        val typeface = FindFonts.typeface(this)
        val paint = android.graphics.Paint().apply { this.typeface = typeface; textSize = 48f }
        for (text in listOf(PREVIEW_TITLE, PREVIEW_BODY)) {
            val glyphs = android.graphics.text.TextRunShaper.shapeTextRun(
                text, 0, text.length, 0, text.length, 0f, 0f, /* isRtl = */ isAr, paint
            )
            val used = (0 until glyphs.glyphCount()).map { glyphs.getFont(it).file?.name ?: "?" }.distinct()
            Log.i(TAG, "\"$text\" drawn with: $used")
        }
        val digits = "3,000,000"
        val g = android.graphics.text.TextRunShaper.shapeTextRun(
            digits, 0, digits.length, 0, digits.length, 0f, 0f, false, paint
        )
        Log.i(TAG, "\"$digits\" drawn with: ${(0 until g.glyphCount()).map { g.getFont(it).file?.name ?: "?" }.distinct()}")
    }

    private companion object { const val TAG = "FindFonts" }
}
