package com.example.myapplication.utils

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.myapplication.R
import java.util.WeakHashMap

object SwipeRefreshHelper {

    const val MIN_REFRESH_DURATION_MS = 600L

    private val refreshStartTimes = WeakHashMap<SwipeRefreshLayout, Long>()

    /**
     * Triggers the appropriate haptic feedback for a pull-to-refresh activation:
     * GESTURE_THRESHOLD_ACTIVATE on API 34+ (Android 14+), CONTEXT_CLICK otherwise.
     */
    fun performRefreshHaptic(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(constant)
    }

    /**
     * Configures a SwipeRefreshLayout with the brand colors, smooth physics,
     * one short haptic on refresh trigger, and timestamp tracking for min spinner duration.
     */
    fun setup(
        swipeRefresh: SwipeRefreshLayout,
        onRefresh: () -> Unit
    ) {
        val context = swipeRefresh.context
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(context, R.color.find_primary),
            ContextCompat.getColor(context, R.color.find_active_blue)
        )
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(context, R.color.surface_primary)
        )
        // Smooth trigger distance
        val density = context.resources.displayMetrics.density
        swipeRefresh.setDistanceToTriggerSync((80 * density).toInt())
        swipeRefresh.setOnRefreshListener {
            performRefreshHaptic(swipeRefresh)
            refreshStartTimes[swipeRefresh] = SystemClock.uptimeMillis()
            onRefresh()
        }
    }

    /**
     * Manually mark a refresh as started (for programmatic triggers),
     * setting isRefreshing = true and tracking the start time.
     */
    fun startRefreshing(swipeRefresh: SwipeRefreshLayout) {
        swipeRefresh.isRefreshing = true
        refreshStartTimes[swipeRefresh] = SystemClock.uptimeMillis()
    }

    /**
     * Stops refreshing, guaranteeing the spinner remains visible for at least
     * [minDurationMs] (~600 ms) so it never flashes when the network responds instantly.
     */
    fun stopRefreshing(
        swipeRefresh: SwipeRefreshLayout?,
        minDurationMs: Long = MIN_REFRESH_DURATION_MS
    ) {
        if (swipeRefresh == null) return
        val startTime = refreshStartTimes[swipeRefresh] ?: 0L
        val elapsed = SystemClock.uptimeMillis() - startTime
        val remaining = minDurationMs - elapsed
        if (startTime > 0 && remaining > 0) {
            swipeRefresh.postDelayed({
                swipeRefresh.isRefreshing = false
                refreshStartTimes.remove(swipeRefresh)
            }, remaining)
        } else {
            swipeRefresh.isRefreshing = false
            refreshStartTimes.remove(swipeRefresh)
        }
    }
}
