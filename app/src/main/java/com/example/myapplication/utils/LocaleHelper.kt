package com.example.myapplication.utils

import android.content.Context
import java.util.Locale

object LocaleHelper {
    /**
     * Returns true if the current app locale is Arabic (RTL).
     */
    fun isArabic(context: Context): Boolean {
        val lang = context.resources.configuration.locales[0].language
        return lang == "ar"
    }

    /**
     * Pick the correct name based on the current locale:
     * - If Arabic → nameAr (fallback: nameEn)
     * - If English → nameEn (fallback: nameAr)
     */
    fun localizedName(context: Context, nameAr: String, nameEn: String?): String {
        return if (isArabic(context)) {
            nameAr.ifBlank { nameEn ?: nameAr }
        } else {
            nameEn?.ifBlank { nameAr } ?: nameAr
        }
    }
}
