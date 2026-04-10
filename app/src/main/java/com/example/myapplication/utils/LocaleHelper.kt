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
     */
    fun applyLocale(context: Context) {
        val lang = getLanguage(context)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    /**
     * Change the language: save to local DB and apply immediately.
     * This will trigger activity recreation by AppCompat.
     */
    fun setLanguage(context: Context, languageCode: String) {
        saveLanguage(context, languageCode)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }

    /**
     * Wrap the base context with the saved locale.
     * Call from Activity.attachBaseContext() for reliable locale injection.
     */
    fun wrap(context: Context): Context {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}
