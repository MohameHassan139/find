package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setupAppBarDirection()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        setupAppBarDirection()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        setupAppBarDirection()
    }

    private fun setupAppBarDirection() {
        findViewById<View>(R.id.llAppBar)?.let {
            com.example.myapplication.utils.LocaleHelper.applyAppBarDirection(it)
        }
        findViewById<View>(R.id.appBarBack)?.let {
            com.example.myapplication.utils.LocaleHelper.applyAppBarDirection(it)
        }
    }

    /**
     * Call after setContentView().
     * Pads the root view top by status bar height.
     * Pads the bottom nav bottom by navigation bar height.
     */
    protected fun applyWindowInsets(
        appBarId: Int = R.id.llAppBar,
        bottomNavId: Int = R.id.cvBottomNav
    ) {
        // Navigation bar → adjust bottom nav margin
        if (bottomNavId != View.NO_ID) {
            findViewById<View>(bottomNavId)?.let { nav ->
                val origBottom = (nav.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
                    val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    val lp = nav.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null) {
                        lp.bottomMargin = origBottom + nb
                        nav.layoutParams = lp
                    }
                    insets
                }
            }
        }
    }
}
