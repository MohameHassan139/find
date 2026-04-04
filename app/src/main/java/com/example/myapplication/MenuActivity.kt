package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.profile.MyAdsActivity
import com.example.myapplication.profile.ProfileActivity
import com.example.myapplication.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show user name if logged in
        if (TokenManager.isLoggedIn(this)) {
            val name = TokenManager.getName(this)
            if (name.isNotEmpty()) binding.btnLogin.text = name
            else binding.btnLogin.text = "حسابي"
        }

        binding.btnClose.setOnClickListener {
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
            Toast.makeText(this, getString(R.string.menu_notifications), Toast.LENGTH_SHORT).show()
        }
        binding.menuSettings.setOnClickListener {
            Toast.makeText(this, getString(R.string.menu_settings), Toast.LENGTH_SHORT).show()
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
}
