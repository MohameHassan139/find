package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class CommunicationChannelsActivity : BaseActivity() {

    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private lateinit var prefs: SharedPreferences
    private var authDialog: AlertDialog? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_communication_channels)
        applyWindowInsets()

        prefs = getSharedPreferences("communication_prefs", Context.MODE_PRIVATE)

        HomeHeaderHelper.attach(
            this,
            findViewById(android.R.id.content),
            sharedVm.categories
        )
        BottomNavHelper.setup(this, NavScreen.NONE)

        setupAppBar()
        setupCheckboxes()
    }

    override fun onResume() {
        super.onResume()
        checkAuthGate()
    }

    private fun setupAppBar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener { finishWithPop() }
    }

    /**
     * Auth gate matching iOS .requiresAuth { router.navigateMenu(to: .settings) }
     * Guests see an alert asking them to log in; canceling finishes the screen.
     */
    private fun checkAuthGate(): Boolean {
        if (TokenManager.isLoggedIn(this)) return true

        if (authDialog?.isShowing == true) return false

        authDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.auth_guard_title))
            .setMessage(getString(R.string.auth_guard_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.auth_guard_go_login)) { _, _ ->
                val loginIntent = Intent(this, PhoneAuthActivity::class.java).apply {
                    putExtra("EXTRA_TARGET_INTENT", intent)
                }
                startActivity(loginIntent)
                finishWithPop()
            }
            .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                finishWithPop()
            }
            .create().apply { show() }

        return false
    }

    private fun setupCheckboxes() {
        val cbInApp = findViewById<CheckBox>(R.id.cbInApp)
        val cbWhatsapp = findViewById<CheckBox>(R.id.cbWhatsapp)
        val cbCall = findViewById<CheckBox>(R.id.cbCall)
        val rowWhatsapp = findViewById<View>(R.id.rowWhatsapp)
        val rowCall = findViewById<View>(R.id.rowCall)

        // In-app Chat is always enabled and locked (matching iOS AppChannels.chat: true, isLocked: true)
        cbInApp.isChecked = true
        cbInApp.isEnabled = false

        // Load cached values first for instantaneous UI
        cbWhatsapp.isChecked = prefs.getBoolean("whatsapp", false)
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

            // Tapping row also toggles checkmark (matching iOS ChannelRow tap behavior)
            rowWhatsapp?.setOnClickListener {
                cbWhatsapp.isChecked = !cbWhatsapp.isChecked
            }
            rowCall?.setOnClickListener {
                cbCall.isChecked = !cbCall.isChecked
            }
        }

        val token = TokenManager.getToken(this)
        if (token == null) {
            attachListeners()
            return
        }

        // Reconcile with server (GET /auth/me) before wiring listeners
        lifecycleScope.launch {
            // fetchMe() also refreshes the cached user in TokenManager.
            AppContainer.auth.fetchMe().getOrNull()?.let { user ->
                cbWhatsapp.isChecked = user.whatsappEnabled
                cbCall.isChecked = user.callEnabled
                prefs.edit()
                    .putBoolean("whatsapp", user.whatsappEnabled)
                    .putBoolean("call", user.callEnabled)
                    .apply()
            }
            attachListeners()
        }
    }

    /**
     * Syncs toggles to server via PATCH /user/profile.
     * Matches iOS AppChannels.save() contract:
     * Keeps name attached so server doesn't interpret its absence as a clear,
     * guards against wiping name with an empty string,
     * and updates TokenManager so rest of app sees the new user state.
     */
    private fun syncChannelsToServer(whatsappEnabled: Boolean, callEnabled: Boolean) {
        if (TokenManager.getToken(this) == null) return

        lifecycleScope.launch {
            var name = TokenManager.getName(this@CommunicationChannelsActivity)
            if (name.isEmpty()) {
                name = AppContainer.auth.fetchMe().getOrNull()?.name.orEmpty()
            }
            // Guard against empty name — exactly as in iOS AppChannels.save()
            if (name.isEmpty()) return@launch

            val request = UpdateProfileRequest(
                name = name,
                whatsappEnabled = whatsappEnabled,
                callEnabled = callEnabled
            )
            // updateProfile() also refreshes the cached user in TokenManager.
            when (val result = AppContainer.auth.updateProfile(request)) {
                is ApiResult.Success -> result.data?.let { user ->
                    prefs.edit()
                        .putBoolean("whatsapp", user.whatsappEnabled)
                        .putBoolean("call", user.callEnabled)
                        .apply()
                }
                is ApiResult.HttpError -> this@CommunicationChannelsActivity.toast(R.string.error_save_failed)
                is ApiResult.NetworkError -> this@CommunicationChannelsActivity.toast(R.string.error_connection_failed)
            }
        }
    }
}
