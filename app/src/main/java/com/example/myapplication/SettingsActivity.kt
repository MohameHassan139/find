package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.myapplication.utils.LocaleHelper

class SettingsActivity : BaseActivity() {

    companion object {
        private const val PREFS_THEME = "theme_prefs"
        private const val KEY_THEME = "selected_theme"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"

        fun saveTheme(context: Context, theme: String) {
            context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, theme).apply()
        }

        fun getSavedTheme(context: Context): String {
            return context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        }

        fun applyTheme(context: Context) {
            when (getSavedTheme(context)) {
                THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyWindowInsets()

        setupAppBar()
        setupThemeChips()
        setupLanguageChips()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finish() }
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    private val chipLight   get() = findViewById<TextView>(R.id.chipLight)
    private val chipDark    get() = findViewById<TextView>(R.id.chipDark)
    private val chipSystem  get() = findViewById<TextView>(R.id.chipSystem)
    private val tvCurrentTheme get() = findViewById<TextView>(R.id.tvCurrentTheme)

    private fun setupThemeChips() {
        refreshThemeChips(getSavedTheme(this))

        chipLight.setOnClickListener  { applyAndSaveTheme(THEME_LIGHT) }
        chipDark.setOnClickListener   { applyAndSaveTheme(THEME_DARK) }
        chipSystem.setOnClickListener { applyAndSaveTheme(THEME_SYSTEM) }
    }

    private fun applyAndSaveTheme(theme: String) {
        saveTheme(this, theme)
        refreshThemeChips(theme)
        when (theme) {
            THEME_LIGHT  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK   -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else         -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun refreshThemeChips(active: String) {
        setChipActive(chipLight,  active == THEME_LIGHT)
        setChipActive(chipDark,   active == THEME_DARK)
        setChipActive(chipSystem, active == THEME_SYSTEM)
        tvCurrentTheme.text = when (active) {
            THEME_LIGHT  -> getString(R.string.settings_theme_light)
            THEME_DARK   -> getString(R.string.settings_theme_dark)
            else         -> getString(R.string.settings_theme_system)
        }
    }

    // ── Language ─────────────────────────────────────────────────────────────

    private val chipArabic  get() = findViewById<TextView>(R.id.chipArabic)
    private val chipEnglish get() = findViewById<TextView>(R.id.chipEnglish)
    private val tvCurrentLang get() = findViewById<TextView>(R.id.tvCurrentLang)

    private fun setupLanguageChips() {
        refreshLangChips(LocaleHelper.getLanguage(this))

        chipArabic.setOnClickListener  { applyLanguage("ar") }
        chipEnglish.setOnClickListener { applyLanguage("en") }
    }

    private fun applyLanguage(lang: String) {
        if (LocaleHelper.getLanguage(this) == lang) return
        LocaleHelper.setLanguage(this, lang)
        // AppCompat will recreate the activity automatically
    }

    private fun refreshLangChips(active: String) {
        setChipActive(chipArabic,  active == "ar")
        setChipActive(chipEnglish, active == "en")
        tvCurrentLang.text = if (active == "ar")
            getString(R.string.settings_lang_arabic)
        else
            getString(R.string.settings_lang_english)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setChipActive(chip: TextView, active: Boolean) {
        chip.setBackgroundResource(
            if (active) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
        )
        chip.setTextColor(
            if (active)
                getColor(R.color.white)
            else
                getColor(R.color.text_secondary)
        )
    }
}
