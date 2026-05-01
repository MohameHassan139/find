package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
