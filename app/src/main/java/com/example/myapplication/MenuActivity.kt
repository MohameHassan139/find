package com.example.myapplication

import com.example.myapplication.R

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.profile.MyAdsActivity
import com.example.myapplication.profile.ProfileActivity
import com.example.myapplication.databinding.ActivityMenuBinding
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show user name if logged in
        if (TokenManager.isLoggedIn(this)) {
            val name = TokenManager.getName(this)
            if (name.isNotEmpty()) binding.btnLogin.text = name
            else binding.btnLogin.text = getString(R.string.kt_str_c0f5269a)
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(0, android.R.anim.slide_out_right)
        }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            finish()
            overridePendingTransition(0, android.R.anim.slide_out_right)
        }

        binding.btnLogin.setOnClickListener {
            if (TokenManager.isLoggedIn(this)) {
                // Logout
                TokenManager.clear(this)
                startActivity(Intent(this, PhoneAuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                startActivity(Intent(this, PhoneAuthActivity::class.java))
            }
        }

        binding.menuMyAccount.setOnClickListener {
            if (TokenManager.isLoggedIn(this)) startActivity(Intent(this, ProfileActivity::class.java))
            else startActivity(Intent(this, PhoneAuthActivity::class.java))
        }
        binding.menuMyAds.setOnClickListener {
            if (TokenManager.isLoggedIn(this)) startActivity(Intent(this, MyAdsActivity::class.java))
            else startActivity(Intent(this, PhoneAuthActivity::class.java))
        }
        binding.menuFavorites.setOnClickListener {
            Toast.makeText(this, getString(R.string.menu_favorites), Toast.LENGTH_SHORT).show()
        }
        binding.menuNotifications.setOnClickListener {
            if (TokenManager.isLoggedIn(this)) {
                startActivity(Intent(this, com.example.myapplication.notifications.NotificationsActivity::class.java))
            } else {
                startActivity(Intent(this, PhoneAuthActivity::class.java))
            }
        }
        binding.menuSettings.setOnClickListener {
            Toast.makeText(this, getString(R.string.menu_settings), Toast.LENGTH_SHORT).show()
        }
        binding.menuLanguage.setOnClickListener {
            val languages = arrayOf("English", "العربية")
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_language))
                .setItems(languages) { _, which ->
                    val locale = if (which == 0) "en" else "ar"
                    // Save to local database and apply — triggers activity recreation
                    LocaleHelper.setLanguage(this, locale)
                }
                .show()
        }
        binding.menuShareApp.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out the Find app!")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share_app)))
        }
        binding.menuAbout.setOnClickListener {
            Toast.makeText(this, getString(R.string.menu_about), Toast.LENGTH_SHORT).show()
        }
        binding.menuContact.setOnClickListener {
            Toast.makeText(this, getString(R.string.menu_contact), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, android.R.anim.slide_out_right)
    }

    override fun onResume() {
        super.onResume()
        if (TokenManager.isLoggedIn(this)) fetchNotifBadge()
    }

    private fun fetchNotifBadge() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@MenuActivity).getNotifications()
                if (response.isSuccessful) {
                    val body = response.body()
                    // Try meta.unread_count first, fall back to counting is_read=false in data
                    val unread = body?.meta?.unreadCount
                        ?: body?.unreadCount
                        ?: body?.data?.count { !it.isRead }
                        ?: 0
                    if (unread > 0) {
                        binding.tvNotifBadge.text = if (unread > 99) "99+" else unread.toString()
                        binding.tvNotifBadge.visibility = View.VISIBLE
                    } else {
                        binding.tvNotifBadge.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
