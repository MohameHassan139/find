package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.auth.AuthRetrofitClient
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private val sharedVm: SharedCategoriesViewModel by viewModels()

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

        HomeHeaderHelper.attach(this, findViewById(android.R.id.content), sharedVm.categories)

        setupAppBar()
        setupNavigation()
        setupNightMode()
        setupDeleteAccount()
        setupThemeChips()
        setupLanguageChips()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finish() }
    }

    private fun setupNavigation() {
        findViewById<android.view.View>(R.id.rowLanguage).setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowChannels).setOnClickListener {
            startActivity(Intent(this, CommunicationChannelsActivity::class.java))
        }
    }

    private fun setupNightMode() {
        val switchNightMode = findViewById<SwitchCompat>(R.id.switchNightMode)
        val currentTheme = getSavedTheme(this)
        
        switchNightMode.isChecked = currentTheme == THEME_DARK
        
        switchNightMode.setOnCheckedChangeListener { _, isChecked ->
            val theme = if (isChecked) THEME_DARK else THEME_LIGHT
            saveTheme(this, theme)
            applyTheme(this)
        }
    }

    private fun setupDeleteAccount() {
        findViewById<TextView>(R.id.btnDeleteAccount).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_delete_account_title))
                .setMessage(getString(R.string.settings_delete_account_message))
                .setPositiveButton(getString(R.string.settings_delete_account_confirm)) { _, _ ->
                    deleteAccountFromServer()
                }
                .setNegativeButton(getString(R.string.settings_delete_account_cancel), null)
                .show()
        }
    }

    private fun deleteAccountFromServer() {
        val token = TokenManager.getToken(this)
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService
                    .deleteAccount("Bearer $token")
                
                if (response.isSuccessful) {
                    val message = response.body()?.message ?: "Account deleted successfully"
                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
                    
                    TokenManager.clear(this@SettingsActivity)
                    startActivity(Intent(this@SettingsActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Failed to delete account: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
