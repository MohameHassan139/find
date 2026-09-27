package com.example.myapplication.utils

import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.myapplication.R

object SwipeRefreshHelper {
    /**
     * Configures a SwipeRefreshLayout with the brand colors and smooth physics.
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
        swipeRefresh.setOnRefreshListener(onRefresh)
    }
}
