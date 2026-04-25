package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CheckBox
import androidx.activity.viewModels
import com.example.myapplication.utils.LocaleHelper

class CommunicationChannelsActivity : BaseActivity() {

    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_communication_channels)
        applyWindowInsets()

        prefs = getSharedPreferences("communication_prefs", Context.MODE_PRIVATE)

        com.example.myapplication.utils.HomeHeaderHelper.attach(
            this,
            findViewById(android.R.id.content),
            sharedVm.categories
        )

        setupAppBar()
        setupCheckboxes()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finish() }
    }

    private fun setupCheckboxes() {
        val cbInApp = findViewById<CheckBox>(R.id.cbInApp)
        val cbWhatsapp = findViewById<CheckBox>(R.id.cbWhatsapp)
        val cbCall = findViewById<CheckBox>(R.id.cbCall)

        // Load saved preferences
        cbInApp.isChecked = prefs.getBoolean("in_app", true)
        cbWhatsapp.isChecked = prefs.getBoolean("whatsapp", true)
        cbCall.isChecked = prefs.getBoolean("call", false)

        // Save preferences on change
        cbInApp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("in_app", isChecked).apply()
        }

        cbWhatsapp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("whatsapp", isChecked).apply()
        }

        cbCall.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("call", isChecked).apply()
        }
    }
}
