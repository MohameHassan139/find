package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import com.example.myapplication.utils.LocaleHelper

class LanguageActivity : BaseActivity() {

    private val sharedVm: SharedCategoriesViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)
        applyWindowInsets()

        com.example.myapplication.utils.HomeHeaderHelper.attach(
            this,
            findViewById(android.R.id.content),
            sharedVm.categories
        )

        setupAppBar()
        setupLanguageButtons()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finish() }
    }

    private fun setupLanguageButtons() {
        val btnArabic = findViewById<TextView>(R.id.btnArabic)
        val btnEnglish = findViewById<TextView>(R.id.btnEnglish)
        
        val currentLang = LocaleHelper.getLanguage(this)
        
        // Update UI based on current language
        updateLanguageButtons(currentLang)
        
        btnArabic.setOnClickListener {
            if (LocaleHelper.getLanguage(this) != "ar") {
                LocaleHelper.setLanguage(this, "ar")
            }
        }
        
        btnEnglish.setOnClickListener {
            if (LocaleHelper.getLanguage(this) != "en") {
                LocaleHelper.setLanguage(this, "en")
            }
        }
    }
    
    private fun updateLanguageButtons(currentLang: String) {
        val btnArabic = findViewById<TextView>(R.id.btnArabic)
        val btnEnglish = findViewById<TextView>(R.id.btnEnglish)
        
        if (currentLang == "ar") {
            btnArabic.setBackgroundResource(R.drawable.bg_language_selected)
            btnArabic.setTextColor(getColor(R.color.find_active_blue))
            btnEnglish.setBackgroundResource(R.drawable.bg_language_unselected)
            btnEnglish.setTextColor(getColor(R.color.text_primary))
        } else {
            btnEnglish.setBackgroundResource(R.drawable.bg_language_selected)
            btnEnglish.setTextColor(getColor(R.color.find_active_blue))
            btnArabic.setBackgroundResource(R.drawable.bg_language_unselected)
            btnArabic.setTextColor(getColor(R.color.text_primary))
        }
    }
}
