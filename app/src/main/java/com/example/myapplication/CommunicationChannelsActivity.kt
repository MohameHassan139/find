package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

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
        BottomNavHelper.setup(this, NavScreen.NONE)

        setupAppBar()
        setupCheckboxes()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finishWithPop() }
    }

    private fun setupCheckboxes() {
        val cbInApp = findViewById<CheckBox>(R.id.cbInApp)
        val cbWhatsapp = findViewById<CheckBox>(R.id.cbWhatsapp)
        val cbCall = findViewById<CheckBox>(R.id.cbCall)

        // Local cache first (instant UI), reconciled with the server below —
        // another client (iOS/Web) may have changed these since our last sync.
        cbInApp.isChecked = true
        cbWhatsapp.isChecked = prefs.getBoolean("whatsapp", true)
        cbCall.isChecked = prefs.getBoolean("call", false)

        fun attachListeners() {
            cbWhatsapp.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("whatsapp", isChecked).apply()
                syncChannelsToServer(whatsappEnabled = isChecked, callEnabled = cbCall.isChecked)
            }
            cbCall.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("call", isChecked).apply()
                syncChannelsToServer(whatsappEnabled = cbWhatsapp.isChecked, callEnabled = isChecked)
            }
        }

        val token = TokenManager.getToken(this)
        if (token == null) {
            attachListeners()
            return
        }

        // Reconcile with the server before wiring listeners, so the refresh
        // itself doesn't trigger a redundant PATCH back to the server.
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@CommunicationChannelsActivity).getMe()
                val user = response.body()?.user
                if (response.isSuccessful && user != null) {
                    cbWhatsapp.isChecked = user.whatsappEnabled
                    cbCall.isChecked = user.callEnabled
                    prefs.edit()
                        .putBoolean("whatsapp", user.whatsappEnabled)
                        .putBoolean("call", user.callEnabled)
                        .apply()
                }
            } catch (_: Exception) {
            } finally {
                attachListeners()
            }
        }
    }

    private fun syncChannelsToServer(whatsappEnabled: Boolean, callEnabled: Boolean) {
        if (TokenManager.getToken(this) == null) return
        val name = TokenManager.getName(this)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@CommunicationChannelsActivity).updateProfile(
                    UpdateProfileRequest(name, whatsappEnabled, callEnabled)
                )
                if (!response.isSuccessful) {
                    Toast.makeText(this@CommunicationChannelsActivity, getString(R.string.kt_str_c5572cc3), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@CommunicationChannelsActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
