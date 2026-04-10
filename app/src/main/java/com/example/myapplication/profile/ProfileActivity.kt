package com.example.myapplication.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.MenuActivity
import com.example.myapplication.auth.AuthRetrofitClient
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.databinding.ActivityProfileBinding
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnSignOut.setOnClickListener { confirmSignOut() }
        binding.btnMyAds.setOnClickListener {
            startActivity(Intent(this, MyAdsActivity::class.java))
        }

        loadProfile()
    }

    private fun loadProfile() {
        val token = TokenManager.getToken(this) ?: return
        val name = TokenManager.getName(this)
        val phone = TokenManager.getPhone(this)
        val avatar = TokenManager.getAvatar(this)

        binding.etName.setText(name)
        binding.tvPhone.text = phone

        if (avatar.isNotEmpty()) {
            Glide.with(this).load(avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(binding.ivAvatar)
        }

        // Refresh from server
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.getMe("Bearer $token")
                if (response.isSuccessful) {
                    val user = response.body()?.user ?: return@launch
                    binding.etName.setText(user.name ?: "")
                    binding.tvPhone.text = user.phone ?: ""
                    if (!user.avatar.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity).load(user.avatar)
                            .placeholder(R.drawable.ic_avatar_placeholder)
                            .circleCrop()
                            .into(binding.ivAvatar)
                        TokenManager.save(this@ProfileActivity, token,
                            user.name ?: "", user.phone ?: "", user.avatar)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.kt_str_a0139053), Toast.LENGTH_SHORT).show()
            return
        }
        val token = TokenManager.getToken(this) ?: return
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.updateProfile(
                    "Bearer $token", UpdateProfileRequest(name)
                )
                if (response.isSuccessful) {
                    val user = response.body()?.user
                    TokenManager.save(this@ProfileActivity, token,
                        user?.name ?: name, user?.phone ?: "", user?.avatar ?: "")
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_201aed2e), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_c5572cc3), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setMessage("هل تريد تسجيل الخروج؟")
            .setPositiveButton("خروج") { _, _ -> signOut() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun signOut() {
        val token = TokenManager.getToken(this)
        lifecycleScope.launch {
            try {
                if (token != null)
                    AuthRetrofitClient.authService.signOut("Bearer $token")
            } catch (_: Exception) {}
            TokenManager.clear(this@ProfileActivity)
            startActivity(Intent(this@ProfileActivity, PhoneAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
