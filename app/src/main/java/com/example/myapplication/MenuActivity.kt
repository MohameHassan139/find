package com.example.myapplication

import com.example.myapplication.R

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.example.myapplication.BaseActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.favorites.FavoritesActivity
import com.example.myapplication.profile.MyAdsActivity
import com.example.myapplication.profile.ProfileActivity
import com.example.myapplication.databinding.ActivityMenuBinding
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.AuthGuard
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.SharedCategoriesViewModel
import androidx.activity.viewModels
import kotlinx.coroutines.launch
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer

class MenuActivity : BaseActivity() {

    private lateinit var binding: ActivityMenuBinding
    private val sharedVm: SharedCategoriesViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)
        BottomNavHelper.setup(this, NavScreen.NONE)

        // Hide top login box when already logged in to prevent duplicate "الملف الشخصي" box
        if (TokenManager.isLoggedIn(this)) {
            binding.btnLogin.visibility = View.GONE
        } else {
            binding.btnLogin.visibility = View.VISIBLE
            binding.btnLogin.text = getString(R.string.login_create_account)
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack)?.setOnClickListener {
            finishMenuActivity()
        }
        findViewById<android.widget.ImageButton>(R.id.btnMenu)?.apply {
            setImageResource(R.drawable.ic_menu_circle_blue)
            imageTintList = null
            setOnClickListener { finishMenuActivity() }
        }

        binding.btnLogin.setOnClickListener {
            if (TokenManager.isLoggedIn(this)) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.logout))
                    .setMessage(getString(R.string.logout_confirm_message))
                    .setPositiveButton(getString(R.string.logout)) { _, _ ->
                        TokenManager.clear(this)
                        binding.btnLogin.text = getString(R.string.login_create_account)
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    }
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show()
            } else {
                startActivity(Intent(this, PhoneAuthActivity::class.java))
            }
        }

        binding.menuMyAccount.setOnClickListener {
            val target = Intent(this, ProfileActivity::class.java)
            AuthGuard.requireLogin(this, target) { startWithPush(target) }
        }
        binding.menuMyAds.setOnClickListener {
            val target = Intent(this, MyAdsActivity::class.java)
            AuthGuard.requireLogin(this, target) { startWithPush(target) }
        }
        binding.menuFavorites.setOnClickListener {
            val target = Intent(this, FavoritesActivity::class.java)
            AuthGuard.requireLogin(this, target) { startWithPush(target) }
        }
        binding.menuNotifications.setOnClickListener {
            val target = Intent(this, com.example.myapplication.notifications.NotificationsActivity::class.java)
            AuthGuard.requireLogin(this, target) { startWithPush(target) }
        }
        binding.menuSettings.setOnClickListener {
            startWithPush(Intent(this, SettingsActivity::class.java))
        }
        binding.menuShareApp.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out the Find app!")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share_app)))
        }
        binding.menuAbout.setOnClickListener {
            toast(R.string.menu_about)
        }
        binding.menuContact.setOnClickListener {
            toast(R.string.menu_contact)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finishMenuActivity()
    }

    override fun onResume() {
        super.onResume()
        if (TokenManager.isLoggedIn(this)) fetchNotifBadge()
    }

    private fun fetchNotifBadge() {
        lifecycleScope.launch {
            val unread = AppContainer.notifications.unreadCount().getOrNull() ?: return@launch
            if (unread > 0) {
                binding.tvNotifBadge.text = if (unread > 99) "99+" else unread.toString()
                binding.tvNotifBadge.visibility = View.VISIBLE
            } else {
                binding.tvNotifBadge.visibility = View.GONE
            }
        }
    }
}
