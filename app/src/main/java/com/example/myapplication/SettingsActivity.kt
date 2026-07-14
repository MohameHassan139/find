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
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
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
        BottomNavHelper.setup(this, NavScreen.NONE)

        setupAppBar()
        setupNavigation()
        setupNightMode()
        setupDeleteAccount()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finishWithPop() }
    }

    private fun setupNavigation() {
        findViewById<android.view.View>(R.id.rowLanguage).setOnClickListener {
            startWithPush(Intent(this, LanguageActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowChannels).setOnClickListener {
            startWithPush(Intent(this, CommunicationChannelsActivity::class.java))
        }
    }

    private fun setupNightMode() {
        val switchNightMode = findViewById<SwitchCompat>(R.id.switchNightMode)
        val currentTheme = getSavedTheme(this)
        
        val isChecked = currentTheme == THEME_DARK
        switchNightMode.isChecked = isChecked
        
        val greenColor = androidx.core.content.ContextCompat.getColor(this, R.color.toggle_active_green)
        val greyColor = androidx.core.content.ContextCompat.getColor(this, R.color.switch_inactive_track)
        switchNightMode.trackTintList = android.content.res.ColorStateList.valueOf(
            if (isChecked) greenColor else greyColor
        )
        
        switchNightMode.setOnCheckedChangeListener { _, checked ->
            switchNightMode.trackTintList = android.content.res.ColorStateList.valueOf(
                if (checked) greenColor else greyColor
            )
            val theme = if (checked) THEME_DARK else THEME_LIGHT
            saveTheme(this@SettingsActivity, theme)
            applyTheme(this@SettingsActivity)
        }
    }

    private fun setupDeleteAccount() {
        findViewById<android.view.View>(R.id.btnDeleteAccount).setOnClickListener {
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

}
