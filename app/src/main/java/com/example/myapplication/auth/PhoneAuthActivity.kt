package com.example.myapplication.auth

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.BaseActivity
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.MainActivity
import com.example.myapplication.NavScreen
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.crash.CrashReporting
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.AppContainer
import com.example.myapplication.databinding.ActivityPhoneAuthBinding
import com.example.myapplication.push.PushTokenManager
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.toast
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class PhoneAuthActivity : BaseActivity() {

    companion object {
        const val OTP_LENGTH = 4
    }

    private lateinit var binding: ActivityPhoneAuthBinding
    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private var phoneNumber = ""
    private var resendTimer: CountDownTimer? = null
    private var isVerifyInFlight = false
    private var isReceiverRegistered = false

    private val smsConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            if (message != null) {
                val matcher = Pattern.compile("(\\d{$OTP_LENGTH})").matcher(message)
                if (matcher.find()) {
                    val code = matcher.group(1)
                    if (!code.isNullOrEmpty()) {
                        binding.etOtp.setText(code)
                        binding.etOtp.setSelection(code.length)
                    }
                }
            }
        }
    }

    private val smsConsentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
                val extras = intent.extras ?: return
                @Suppress("DEPRECATION")
                val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        @Suppress("DEPRECATION")
                        val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                        consentIntent?.let {
                            try {
                                smsConsentLauncher.launch(it)
                            } catch (_: Exception) {}
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        // 5-minute consent timeout must fail silently; user can still type code
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)

        if (TokenManager.isLoggedIn(this)) { goToMain(); return }

        binding = ActivityPhoneAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)
        BottomNavHelper.setup(this, NavScreen.NONE)

        val handleBack = {
            if (binding.layoutOtp.visibility == View.VISIBLE) {
                showPhoneStep()
            } else if (!isTaskRoot) {
                finishWithPop()
            } else {
                navigateToHome()
            }
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack)?.setOnClickListener { handleBack() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu)?.setOnClickListener {
            startMenuActivity()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        setupOtpField()
        showPhoneStep()

        binding.btnSendOtp.setOnClickListener { handleSendOtp() }
        binding.btnVerifyOtp.setOnClickListener { handleVerifyOtp() }
        binding.btnChangePhone.setOnClickListener { showPhoneStep() }
        binding.tvResend.setOnClickListener {
            if (binding.tvResend.isEnabled) requestOtp()
        }
    }

    private fun setupOtpField() {
        binding.etOtp.filters = arrayOf(InputFilter.LengthFilter(OTP_LENGTH))
        binding.etOtp.doAfterTextChanged { s ->
            val text = s?.toString() ?: ""
            val digitsOnly = text.filter { it.isDigit() }
            if (digitsOnly != text) {
                binding.etOtp.setText(digitsOnly)
                binding.etOtp.setSelection(digitsOnly.length)
                return@doAfterTextChanged
            }
            if (digitsOnly.length == OTP_LENGTH && !isVerifyInFlight) {
                handleVerifyOtp()
            }
        }
    }

    private fun showPhoneStep() {
        resendTimer?.cancel()
        unregisterSmsConsentReceiver()
        binding.layoutPhone.visibility = View.VISIBLE
        binding.layoutOtp.visibility = View.GONE
    }

    private fun showOtpStep() {
        binding.layoutPhone.visibility = View.GONE
        binding.layoutOtp.visibility = View.VISIBLE
        binding.tvOtpHint.text = "${getString(R.string.send_otp_to)} \u200E$phoneNumber\u200E"
        binding.etOtp.text?.clear()
        startResendTimer()
        startSmsConsent()

        binding.etOtp.post {
            binding.etOtp.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etOtp, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun startSmsConsent() {
        try {
            SmsRetriever.getClient(this).startSmsUserConsent(null)
        } catch (_: Exception) {}
        registerSmsConsentReceiver()
    }

    private fun registerSmsConsentReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        ContextCompat.registerReceiver(
            this,
            smsConsentReceiver,
            filter,
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        isReceiverRegistered = true
    }

    private fun unregisterSmsConsentReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(smsConsentReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    private fun handleSendOtp() {
        val code = binding.etCountryCode.text.toString().trim()
        val number = binding.etPhone.text.toString().trim()
        if (number.isEmpty()) {
            toast(R.string.enter_phone_number)
            return
        }
        phoneNumber = "${code}${number}"
        requestOtp()
    }

    private fun requestOtp() {
        setPhoneLoading(true)
        binding.tvResend.isEnabled = false
        lifecycleScope.launch {
            val result = AppContainer.auth.requestOtp(phoneNumber)
            setPhoneLoading(false)
            when {
                result is ApiResult.Success -> showOtpStep()
                // 429 = 60s resend cooldown or the silent daily safety cap.
                // The server's `message` is already localized (Accept-Language),
                // so show it as-is. There is no attempt limit / temporary block
                // any more, so no "attempts left" or "N days" text.
                result is ApiResult.HttpError && result.code == 429 ->
                    toast(result.message ?: getString(R.string.error_generic), long = true)
                result is ApiResult.HttpError -> {
                    toast(R.string.otp_send_failed)
                    if (binding.layoutOtp.visibility == View.VISIBLE) {
                        binding.tvResend.isEnabled = true
                    }
                }
                else -> {
                    toast(R.string.error_server_unreachable)
                    if (binding.layoutOtp.visibility == View.VISIBLE) {
                        binding.tvResend.isEnabled = true
                    }
                }
            }
        }
    }

    private fun handleVerifyOtp() {
        if (isVerifyInFlight) return
        val code = binding.etOtp.text.toString().trim()
        if (code.length != OTP_LENGTH) {
            toast(R.string.enter_verification_code)
            return
        }
        isVerifyInFlight = true
        setOtpLoading(true)
        lifecycleScope.launch {
            try {
                val result = AppContainer.auth.verifyOtp(phoneNumber, code)
                when (result) {
                    is ApiResult.Success -> {
                        val body = result.data
                        val token = body?.token
                        if (token != null) {
                            TokenManager.save(
                                this@PhoneAuthActivity, token,
                                body.user?.name ?: "", body.user?.phone ?: "",
                                body.user?.avatar ?: "",
                                body.user?.id?.toString() ?: ""
                            )
                            body.user?.id?.toString()?.let { CrashReporting.setUserId(it) }
                            PushTokenManager.refreshAndUploadIfNeededAsync(this@PhoneAuthActivity)
                            goToMain()
                        } else {
                            toast(body?.message ?: getString(R.string.verification_failed))
                        }
                    }
                    is ApiResult.HttpError ->
                        toast(if (result.code == 422) getString(R.string.invalid_verification_code) else getString(R.string.error_failed_to_load, result.code))
                    is ApiResult.NetworkError -> toast(R.string.error_server_unreachable)
                }
            } finally {
                isVerifyInFlight = false
                setOtpLoading(false)
            }
        }
    }

    private fun startResendTimer() {
        binding.tvResend.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(ms: Long) {
                binding.tvResend.text = getString(R.string.resend_after_format, ms / 1000)
            }
            override fun onFinish() {
                binding.tvResend.text = getString(R.string.resend_code)
                binding.tvResend.isEnabled = true
            }
        }.start()
    }

    private fun setPhoneLoading(on: Boolean) {
        binding.btnSendOtp.isEnabled = !on
        binding.btnSendOtp.text = if (on) "" else getString(R.string.send_otp)
        binding.progressPhone.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun setOtpLoading(on: Boolean) {
        binding.btnVerifyOtp.isEnabled = !on
        binding.btnVerifyOtp.text = if (on) "" else getString(R.string.verify)
        binding.progressOtp.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun goToMain() {
        // If we were launched from another activity (e.g. AuthGuard dialog), just finish back to it.
        // If we were launched as the root (e.g. from logout), go to MainActivity.
        @Suppress("DEPRECATION")
        val targetIntent = intent.getParcelableExtra<Intent>("EXTRA_TARGET_INTENT")
        if (targetIntent != null) {
            startActivity(targetIntent)
            finish()
            return
        }

        if (!isTaskRoot) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
        applyPopTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
        unregisterSmsConsentReceiver()
    }
}
