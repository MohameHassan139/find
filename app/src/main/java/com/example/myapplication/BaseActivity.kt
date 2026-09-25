package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.utils.LocaleHelper

open class BaseActivity : AppCompatActivity() {

    fun startWithPush(intent: Intent) {
        startActivity(intent)
        applyPushTransition()
    }

    fun finishWithPop() {
        finish()
        applyPopTransition()
    }

    fun startMenuActivity() {
        startActivity(Intent(this, MenuActivity::class.java))
        @Suppress("DEPRECATION")
        if (LocaleHelper.isArabic(this)) {
            overridePendingTransition(R.anim.slide_in_left, 0)
        } else {
            overridePendingTransition(R.anim.slide_in_right, 0)
        }
    }

    fun finishMenuActivity() {
        finish()
        @Suppress("DEPRECATION")
        if (LocaleHelper.isArabic(this)) {
            overridePendingTransition(0, R.anim.slide_out_left)
        } else {
            overridePendingTransition(0, R.anim.slide_out_right)
        }
    }

    fun applyPushTransition() {
        @Suppress("DEPRECATION")
        if (LocaleHelper.isArabic(this)) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    fun applyPopTransition() {
        @Suppress("DEPRECATION")
        if (LocaleHelper.isArabic(this)) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private var origBottomNavMargin: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inter + Noto Sans Arabic on every TextView (see Type.kt). Must run before super.onCreate.
        FindTypefaceInflater.install(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupStatusBarTheme()
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupStatusBarTheme()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupAppBarDirection()
        applyWindowInsets()
        attachHomeHeaderIfNeeded()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupAppBarDirection()
        applyWindowInsets()
        attachHomeHeaderIfNeeded()
    }

    private var homeHeaderAttached = false

    /**
     * Screens whose layout includes layout_home_header (search + category tabs)
     * but whose Activity may not wire it. Runs after onCreate has finished
     * (posted); if the Activity already wired the header itself (the search
     * box has a click listener), nothing is done, so it never double-attaches.
     */
    private fun attachHomeHeaderIfNeeded() {
        if (homeHeaderAttached) return
        val eligible = this is com.example.myapplication.moderation.BlockedUsersActivity ||
            this is com.example.myapplication.notifications.NotificationsActivity
        if (!eligible) return
        val search = findViewById<View>(R.id.llHomeSearchContainer) ?: return
        if (findViewById<View>(R.id.rvHomeTopTabs) == null) return
        homeHeaderAttached = true
        search.post {
            if (isFinishing || isDestroyed || search.hasOnClickListeners()) return@post
            val sharedVm = androidx.lifecycle.ViewModelProvider(this)[SharedCategoriesViewModel::class.java]
            com.example.myapplication.utils.HomeHeaderHelper.attach(
                this, findViewById(android.R.id.content), sharedVm.categories
            )
            if (!search.hasOnClickListeners()) {
                search.setOnClickListener {
                    startWithPush(Intent(this, SearchActivity::class.java))
                }
            }
        }
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupAppBarDirection()
        applyWindowInsets()
    }

    fun setupStatusBarTheme() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = when (SettingsActivity.getSavedTheme(this)) {
            SettingsActivity.THEME_DARK -> true
            SettingsActivity.THEME_LIGHT -> false
            else -> {
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // Light mode -> dark icons (isAppearanceLightStatusBars = true)
        // Dark mode -> light/white icons (isAppearanceLightStatusBars = false)
        controller.isAppearanceLightStatusBars = !isDark
    }

    private fun setupAppBarDirection() {
        findViewById<View>(R.id.llAppBar)?.let {
            com.example.myapplication.utils.LocaleHelper.applyAppBarDirection(it)
        }
        findViewById<View>(R.id.appBarBack)?.let {
            com.example.myapplication.utils.LocaleHelper.applyAppBarDirection(it)
        }
    }

    fun getSystemStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (36 * resources.displayMetrics.density).toInt()
        }
    }

    /**
     * Call after setContentView().
     * Dynamically adjusts status bar spacer height to match system status bar (notches, punch holes, standard).
     * Adjusts bottom nav margin by navigation bar height.
     */
    protected fun applyWindowInsets(
        appBarId: Int = R.id.llAppBar,
        bottomNavId: Int = R.id.cvBottomNav
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupStatusBarTheme()

        // Apply immediately using known system status bar height so there's zero jump/flicker
        val fallbackHeight = getSystemStatusBarHeight()
        applyStatusBarHeight(fallbackHeight, appBarId)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            val targetStatusHeight = if (statusBarHeight > 0) statusBarHeight else fallbackHeight
            applyStatusBarHeight(targetStatusHeight, appBarId)

            // Navigation bar → adjust bottom nav margin
            if (bottomNavId != View.NO_ID) {
                findViewById<View>(bottomNavId)?.let { nav ->
                    if (origBottomNavMargin == null) {
                        origBottomNavMargin = (nav.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                    }
                    val lp = nav.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null) {
                        lp.bottomMargin = origBottomNavMargin!! + navBarHeight
                        nav.layoutParams = lp
                    }
                }
            }

            // Consume status bar insets so DecorView / ContentFrameLayout never adds duplicate padding
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(window.decorView)
    }

    private fun applyStatusBarHeight(height: Int, appBarId: Int) {
        if (height <= 0) return

        findViewById<View>(R.id.statusBarSpacer)?.let { spacer ->
            val lp = spacer.layoutParams
            if (lp != null && lp.height != height) {
                lp.height = height
                spacer.layoutParams = lp
            }
            if (appBarId != View.NO_ID) {
                findViewById<View>(appBarId)?.let { bar ->
                    if (bar.paddingTop != 0) {
                        bar.setPadding(bar.paddingLeft, 0, bar.paddingRight, bar.paddingBottom)
                    }
                }
            }
        } ?: run {
            if (appBarId != View.NO_ID) {
                findViewById<View>(appBarId)?.let { bar ->
                    if (bar.paddingTop != height) {
                        bar.setPadding(bar.paddingLeft, height, bar.paddingRight, bar.paddingBottom)
                    }
                }
            }
        }

        findViewById<View>(android.R.id.content)?.let { content ->
            if (content.paddingTop != 0) {
                content.setPadding(content.paddingLeft, 0, content.paddingRight, content.paddingBottom)
            }
        }
    }
}
