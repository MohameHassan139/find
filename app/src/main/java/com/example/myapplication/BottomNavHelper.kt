package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity

enum class NavScreen { HOME, ADD, CHAT, NONE }

object BottomNavHelper {

    private val COLOR_SELECTED   = Color.parseColor("#111111")
    private val COLOR_UNSELECTED = Color.parseColor("#AAAAAA")

    fun setup(activity: AppCompatActivity, current: NavScreen) {
        applyItem(activity, R.id.ivNavHome, R.id.tvNavHome, current == NavScreen.HOME)
        applyItem(activity, R.id.ivNavAdd,  R.id.tvNavAdd,  current == NavScreen.ADD)
        applyItem(activity, R.id.ivNavChat, R.id.tvNavChat, current == NavScreen.CHAT)

        activity.findViewById<android.view.View>(R.id.navHome)?.setOnClickListener {
            if (current == NavScreen.HOME) return@setOnClickListener
            activity.startActivity(
                Intent(activity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            activity.overridePendingTransition(0, 0)
        }

        activity.findViewById<android.view.View>(R.id.navAdd)?.setOnClickListener {
            if (current == NavScreen.ADD) return@setOnClickListener
            activity.startActivity(Intent(activity, AddAdActivity::class.java))
            activity.overridePendingTransition(0, 0)
        }

        activity.findViewById<android.view.View>(R.id.navChat)?.setOnClickListener {
            if (current == NavScreen.CHAT) return@setOnClickListener
            if (!TokenManager.isLoggedIn(activity)) {
                activity.startActivity(Intent(activity, PhoneAuthActivity::class.java))
                return@setOnClickListener
            }
            activity.startActivity(Intent(activity, ConversationsActivity::class.java))
            activity.overridePendingTransition(0, 0)
        }
    }

    private fun applyItem(activity: AppCompatActivity, ivId: Int, tvId: Int, selected: Boolean) {
        val color = if (selected) COLOR_SELECTED else COLOR_UNSELECTED
        activity.findViewById<ImageView>(ivId)?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        activity.findViewById<TextView>(tvId)?.setTextColor(color)
    }
}
