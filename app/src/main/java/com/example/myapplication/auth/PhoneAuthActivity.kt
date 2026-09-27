package com.example.myapplication.auth

import com.example.myapplication.R
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.MainActivity
import com.example.myapplication.NavScreen
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.crash.CrashReporting
import com.example.myapplication.databinding.ActivityPhoneAuthBinding
import com.example.myapplication.push.PushTokenManager
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class PhoneAuthActivity : BaseActivity() {

    private lateinit var binding: ActivityPhoneAuthBinding
    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private var phoneNumber = ""
    private var resendTimer: CountDownTimer? = null

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

        showPhoneStep()

        binding.btnSendOtp.setOnClickListener { handleSendOtp() }
        binding.btnVerifyOtp.setOnClickListener { handleVerifyOtp() }
        binding.btnChangePhone.setOnClickListener { showPhoneStep() }
        binding.tvResend.setOnClickListener {
            if (binding.tvResend.isEnabled) requestOtp()
        }
    }

    private fun showPhoneStep() {
        resendTimer?.cancel()
        binding.layoutPhone.visibility = View.VISIBLE
        binding.layoutOtp.visibility = View.GONE
    }

    private fun showOtpStep() {
        binding.layoutPhone.visibility = View.GONE
        binding.layoutOtp.visibility = View.VISIBLE
        binding.tvOtpHint.text = "أرسل رمز التحقق إلى \u200E$phoneNumber\u200E"
        startResendTimer()
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
                result is ApiResult.HttpError -> toast(R.string.otp_send_failed)
                else -> toast(R.string.error_server_unreachable)
            }
        }
    }

    private fun handleVerifyOtp() {
        val code = binding.etOtp.text.toString().trim()
        if (code.length < 4) {
            toast(R.string.enter_verification_code)
            return
        }
        setOtpLoading(true)
        lifecycleScope.launch {
            val result = AppContainer.auth.verifyOtp(phoneNumber, code)
            setOtpLoading(false)
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
                        toast(body?.message ?: "فشل التحقق")
                    }
                }
                is ApiResult.HttpError ->
                    toast(if (result.code == 422) "رمز التحقق غير صحيح" else "خطأ: ${result.code}")
                is ApiResult.NetworkError -> toast(R.string.error_server_unreachable)
            }
        }
    }

    private fun startResendTimer() {
        binding.tvResend.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(ms: Long) {
                binding.tvResend.text = "إعادة الإرسال بعد ${ms / 1000}s"
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
    }
}
