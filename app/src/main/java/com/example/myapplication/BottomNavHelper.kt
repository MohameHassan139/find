package com.example.myapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.data.AppContainer
import com.example.myapplication.utils.AuthGuard
import com.example.myapplication.widgets.LiquidGlassBottomBar
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavScreen { HOME, ADD, CHAT, NONE }

object BottomNavHelper {

    val chatBadgeCount = mutableIntStateOf(0)

    fun updateChatBadge(count: Int) {
        chatBadgeCount.intValue = count.coerceAtLeast(0)
    }

    fun refreshChatBadge(activity: AppCompatActivity) {
        if (!TokenManager.isLoggedIn(activity)) {
            chatBadgeCount.intValue = 0
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = AppContainer.chat.conversations()
                val list = result.getOrNull() ?: return@launch
                val totalUnread = list.sumOf { it.myUnread }
                withContext(Dispatchers.Main) {
                    chatBadgeCount.intValue = totalUnread
                }
            } catch (_: Exception) {}
        }
    }

    fun setup(
        activity: AppCompatActivity,
        current: NavScreen,
        hazeState: HazeState? = null
    ) {
        val composeView = activity.findViewById<ComposeView>(R.id.cvBottomNav) ?: return

        refreshChatBadge(activity)

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            val isDark = when (SettingsActivity.getSavedTheme(activity)) {
                SettingsActivity.THEME_DARK -> true
                SettingsActivity.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            LiquidGlassBottomBar(
                selectedTab = if (current == NavScreen.NONE) null else current,
                onTabSelected = { target ->
                    onTabClick(activity, current, target)
                },
                chatBadge = chatBadgeCount.intValue,
                hazeState = hazeState,
                isDark = isDark
            )
        }
    }

    private fun onTabClick(activity: AppCompatActivity, current: NavScreen, target: NavScreen) {
        when (target) {
            NavScreen.HOME -> {
                if (current == NavScreen.HOME) {
                    if (activity is MainActivity) {
                        activity.resetToHome()
                    }
                    return
                }
                val intent = Intent(activity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXTRA_NAV_SCREEN", "HOME")
                }
                activity.startActivity(intent)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
            NavScreen.ADD -> {
                if (current == NavScreen.ADD) return
                val targetIntent = Intent(activity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXTRA_NAV_SCREEN", "ADD")
                }
                AuthGuard.requireLogin(activity, targetIntent) {
                    activity.startActivity(targetIntent)
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(0, 0)
                }
            }
            NavScreen.CHAT -> {
                if (current == NavScreen.CHAT) return
                val targetIntent = Intent(activity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXTRA_NAV_SCREEN", "CHAT")
                }
                AuthGuard.requireLogin(activity, targetIntent) {
                    activity.startActivity(targetIntent)
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(0, 0)
                }
            }
            NavScreen.NONE -> { /* no-op */ }
        }
    }
}
