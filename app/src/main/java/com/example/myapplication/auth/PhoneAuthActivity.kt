package com.example.myapplication.auth

import com.example.myapplication.R
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MainActivity
import com.example.myapplication.databinding.ActivityPhoneAuthBinding
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

class PhoneAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneAuthBinding
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
        binding.tvOtpHint.text = "أرسل رمز التحقق إلى $phoneNumber"
        startResendTimer()
    }

    private fun handleSendOtp() {
        val code = binding.etCountryCode.text.toString().trim()
        val number = binding.etPhone.text.toString().trim()
        if (number.isEmpty()) {
            Toast.makeText(this, getString(R.string.kt_str_7e371590), Toast.LENGTH_SHORT).show()
            return
        }
        phoneNumber = "${code}${number}"
        requestOtp()
    }

    private fun requestOtp() {
        setPhoneLoading(true)
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.requestOtp(OtpRequest(phoneNumber))
                if (response.isSuccessful) {
                    showOtpStep()
                } else {
                    Toast.makeText(this@PhoneAuthActivity,
                        getString(R.string.kt_str_4605bbeb), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PhoneAuthActivity, getString(R.string.kt_str_6c8b9134), Toast.LENGTH_SHORT).show()
            } finally {
                setPhoneLoading(false)
            }
        }
    }

    private fun handleVerifyOtp() {
        val code = binding.etOtp.text.toString().trim()
        if (code.length < 4) {
            Toast.makeText(this, getString(R.string.kt_str_175b78ed), Toast.LENGTH_SHORT).show()
            return
        }
        setOtpLoading(true)
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.verifyOtp(
                    VerifyOtpRequest(phoneNumber, code)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val token = body?.token
                    if (token != null) {
                        TokenManager.save(
                            this@PhoneAuthActivity, token,
                            body.user?.name ?: "", body.user?.phone ?: "",
                            body.user?.avatar ?: "",
                            body.user?.id?.toString() ?: ""
                        )
                        goToMain()
                    } else {
                        Toast.makeText(this@PhoneAuthActivity,
                            body?.message ?: "فشل التحقق", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@PhoneAuthActivity,
                        if (response.code() == 422) "رمز التحقق غير صحيح"
                        else "خطأ: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PhoneAuthActivity, getString(R.string.kt_str_6c8b9134), Toast.LENGTH_SHORT).show()
            } finally {
                setOtpLoading(false)
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
                binding.tvResend.text = getString(R.string.kt_str_9d645b32)
                binding.tvResend.isEnabled = true
            }
        }.start()
    }

    private fun setPhoneLoading(on: Boolean) {
        binding.btnSendOtp.isEnabled = !on
        binding.progressPhone.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun setOtpLoading(on: Boolean) {
        binding.btnVerifyOtp.isEnabled = !on
        binding.progressOtp.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}
