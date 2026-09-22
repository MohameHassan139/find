package com.example.myapplication

import android.content.Intent
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
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
        applyItem(activity, R.id.navHome, R.id.ivNavHome, R.id.tvNavHome, current == NavScreen.HOME)
        applyItem(activity, R.id.navAdd,  R.id.ivNavAdd,  R.id.tvNavAdd,  current == NavScreen.ADD)
        applyItem(activity, R.id.navChat, R.id.ivNavChat, R.id.tvNavChat, current == NavScreen.CHAT)

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

    /** Normal / bold icon for each nav item. */
    private fun iconRes(ivId: Int, selected: Boolean): Int? = when (ivId) {
        R.id.ivNavHome -> if (selected) R.drawable.ic_nav_home_bold else R.drawable.ic_nav_home
        R.id.ivNavAdd -> if (selected) R.drawable.ic_nav_add_bold else R.drawable.ic_nav_add
        R.id.ivNavChat -> if (selected) R.drawable.ic_nav_chat_bold else R.drawable.ic_nav_chat
        else -> null
    }

    private fun applyItem(activity: AppCompatActivity, containerId: Int, ivId: Int, tvId: Int, selected: Boolean) {
        val iconColor = activity.getColor(if (selected) R.color.nav_icon_selected else R.color.nav_icon_unselected)
        val textColor = activity.getColor(if (selected) R.color.nav_text_selected else R.color.nav_text_unselected)
        activity.findViewById<ImageView>(ivId)?.let { iv ->
            // The selected item also gets the heavier version of its icon
            iconRes(ivId, selected)?.let { iv.setImageResource(it) }
            iv.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        }
        activity.findViewById<TextView>(tvId)?.let { tv ->
            tv.setTextColor(textColor)
            // Client note: the selected item is bold — and noticeably so. The app font
            // ships in Regular only, so synthetic bold alone stays thin; outlining the
            // glyphs with a hairline stroke gives it real weight.
            tv.setTypeface(FindFonts.typeface(activity), if (selected) Typeface.BOLD else Typeface.NORMAL)
            tv.paint.isFakeBoldText = selected
            tv.paint.style = if (selected) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            tv.paint.strokeWidth = if (selected) 0.9f else 0f
            tv.invalidate()
        }
        activity.findViewById<android.view.View>(containerId)?.setBackgroundResource(
            if (selected) R.drawable.bg_nav_item_selected else 0
        )
    }
}
