package com.example.myapplication.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE = "ar"

    /**
     * Save the selected language code to local storage.
     */
    fun saveLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }

    /**
     * Get the stored language code, defaults to Arabic.
     */
    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Returns true if the current saved locale is Arabic (RTL).
     */
    fun isArabic(context: Context): Boolean {
        return getLanguage(context) == "ar"
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

    /**
     * Apply the saved locale to the AppCompat framework.
     * Call this early in Application.onCreate() or in each Activity.onCreate()
     * BEFORE setContentView().
     * Forces Latin numerals ("ar-u-nu-latn") so all app numbers remain in English.
     */
    fun applyLocale(context: Context) {
        val lang = getLanguage(context)
        val tag = if (lang == "ar") "ar-u-nu-latn" else lang
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /**
     * Change the language: save to local DB and apply immediately.
     * This will trigger activity recreation by AppCompat.
     */
    fun setLanguage(context: Context, languageCode: String) {
        saveLanguage(context, languageCode)
        val tag = if (languageCode == "ar") "ar-u-nu-latn" else languageCode
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /**
     * Wrap the base context with the saved locale.
     * Call from Activity.attachBaseContext() for reliable locale injection.
     */
    fun wrap(context: Context): Context {
        val lang = getLanguage(context)
        val locale = if (lang == "ar") {
            Locale.forLanguageTag("ar-u-nu-latn")
        } else {
            Locale(lang)
        }
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(if (lang == "ar") Locale("ar") else locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Replaces any Arabic-Indic ('٠'..'٩') and Eastern Arabic/Persian ('۰'..'۹')
     * numerals with Western Latin/English digits ('0'..'9').
     */
    fun toEnglishNumbers(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val arabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val english = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            val aIdx = arabic.indexOf(c)
            if (aIdx != -1) {
                chars[i] = english[aIdx]
                continue
            }
            val pIdx = persian.indexOf(c)
            if (pIdx != -1) {
                chars[i] = english[pIdx]
            }
        }
        return String(chars)
    }

    /**
     * Apply the custom layout direction to the appbar:
     * - Arabic (ar): LTR (buttons on left, logo on right, back arrow points left)
     * - English (en): RTL (buttons on right, logo on left, back arrow points right)
     */
    fun applyAppBarDirection(appBarView: android.view.View) {
        if (isArabic(appBarView.context)) {
            appBarView.layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
        } else {
            appBarView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }
    }
}
