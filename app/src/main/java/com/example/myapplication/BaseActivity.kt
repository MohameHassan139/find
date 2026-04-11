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
     * Pads the appbar top by status bar height (appbar grows, buttons stay intact).
     * Pads the bottom nav bottom by navigation bar height.
     */
    protected fun applyWindowInsets(
        appBarId: Int = R.id.llAppBar,
        bottomNavId: Int = View.NO_ID
    ) {
        // Appbar: capture original padding ONCE from XML, then add status bar on top
        findViewById<View>(appBarId)?.let { appBar ->
            val origTop    = appBar.paddingTop
            val origBottom = appBar.paddingBottom
            val origLeft   = appBar.paddingLeft
            val origRight  = appBar.paddingRight
            // Allow the appbar to grow taller than its declared height
            (appBar.layoutParams)?.let { lp ->
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                appBar.layoutParams = lp
            }
            ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, insets ->
                val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                v.setPadding(origLeft, origTop + sb, origRight, origBottom)
                insets
            }
        }

        // Bottom nav: capture original margin ONCE, then add nav bar height
        if (bottomNavId != View.NO_ID) {
            findViewById<View>(bottomNavId)?.let { nav ->
                val origBottom = (nav.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                ViewCompat.setOnApplyWindowInsetsListener(nav) { v, insets ->
                    val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null) {
                        lp.bottomMargin = origBottom + nb
                        v.layoutParams = lp
                    }
                    insets
                }
            }
        }
    }
}
