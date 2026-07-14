package com.example.myapplication

import android.content.Intent
import android.graphics.PorterDuff
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.utils.AuthGuard

enum class NavScreen { HOME, ADD, CHAT, NONE }

object BottomNavHelper {

    fun setup(activity: AppCompatActivity, current: NavScreen) {
        applyItem(activity, R.id.ivNavHome, R.id.tvNavHome, current == NavScreen.HOME)
        applyItem(activity, R.id.ivNavAdd,  R.id.tvNavAdd,  current == NavScreen.ADD)
        applyItem(activity, R.id.ivNavChat, R.id.tvNavChat, current == NavScreen.CHAT)

        activity.findViewById<android.view.View>(R.id.navHome)?.setOnClickListener {
            if (current == NavScreen.HOME) {
                if (activity is MainActivity) {
                    activity.resetToHome()
                }
                return@setOnClickListener
            }
            val intent = Intent(activity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_NAV_SCREEN", "HOME")
            }
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }

        activity.findViewById<android.view.View>(R.id.navAdd)?.setOnClickListener {
            if (current == NavScreen.ADD) return@setOnClickListener
            val target = Intent(activity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_NAV_SCREEN", "ADD")
            }
            AuthGuard.requireLogin(activity, target) {
                activity.startActivity(target)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        }

        activity.findViewById<android.view.View>(R.id.navChat)?.setOnClickListener {
            if (current == NavScreen.CHAT) return@setOnClickListener
            val target = Intent(activity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_NAV_SCREEN", "CHAT")
            }
            AuthGuard.requireLogin(activity, target) {
                activity.startActivity(target)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        }
    }

    private fun applyItem(activity: AppCompatActivity, ivId: Int, tvId: Int, selected: Boolean) {
        // Resolve colors from theme so they adapt to light/dark mode automatically
        val colorSelected   = activity.getColor(R.color.text_primary)
        val colorUnselected = activity.getColor(R.color.text_secondary)
        val color = if (selected) colorSelected else colorUnselected
        activity.findViewById<ImageView>(ivId)?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        activity.findViewById<TextView>(tvId)?.setTextColor(color)
    }
}
