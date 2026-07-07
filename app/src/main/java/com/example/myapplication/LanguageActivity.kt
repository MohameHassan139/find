package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.activity.viewModels
import android.os.Bundle
import com.example.myapplication.utils.LocaleHelper
import com.google.android.material.card.MaterialCardView

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
        BottomNavHelper.setup(this, NavScreen.NONE)

        setupAppBar()
        setupLanguageButtons()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finish() }
    }

    private fun setupLanguageButtons() {
        val cardArabic  = findViewById<MaterialCardView>(R.id.btnArabic)
        val cardEnglish = findViewById<MaterialCardView>(R.id.btnEnglish)

        // Set initial state
        updateLanguageButtons(LocaleHelper.getLanguage(this))

        cardArabic.setOnClickListener {
            if (LocaleHelper.getLanguage(this) != "ar") {
                LocaleHelper.setLanguage(this, "ar")
            }
        }

        cardEnglish.setOnClickListener {
            if (LocaleHelper.getLanguage(this) != "en") {
                LocaleHelper.setLanguage(this, "en")
            }
        }
    }

    private fun updateLanguageButtons(currentLang: String) {
        val cardArabic  = findViewById<MaterialCardView>(R.id.btnArabic)
        val cardEnglish = findViewById<MaterialCardView>(R.id.btnEnglish)

        val blueColor      = Color.parseColor("#007AFF")
        val strokeSelected = resources.getDimensionPixelSize(R.dimen.language_stroke_selected)

        if (currentLang == "ar") {
            // Arabic selected
            cardArabic.strokeColor  = blueColor
            cardArabic.strokeWidth  = strokeSelected

            // English unselected
            cardEnglish.strokeColor = Color.TRANSPARENT
            cardEnglish.strokeWidth = 0
        } else {
            // English selected
            cardEnglish.strokeColor = blueColor
            cardEnglish.strokeWidth = strokeSelected

            // Arabic unselected
            cardArabic.strokeColor  = Color.TRANSPARENT
            cardArabic.strokeWidth  = 0
        }
    }
}
