package com.example.myapplication.utils

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.R
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.App

object AuthGuard {

    /**
     * Checks if the user is logged in.
     * If yes, runs [action] immediately.
     * If no, shows a dialog asking the user to log in or cancel.
     */
    fun requireLogin(context: Context, targetIntent: Intent? = null, action: () -> Unit) {
        if (TokenManager.isLoggedIn(context)) {
            action()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.auth_guard_title))
            .setMessage(context.getString(R.string.auth_guard_message))
            .setPositiveButton(context.getString(R.string.auth_guard_go_login)) { _, _ ->
                val loginIntent = Intent(context, PhoneAuthActivity::class.java).apply {
                    if (targetIntent != null) {
                        putExtra("EXTRA_TARGET_INTENT", targetIntent)
                    }
                }
                context.startActivity(loginIntent)
            }
            .setNegativeButton(context.getString(R.string.action_cancel), null)
            .show()
    }

    /**
     * Call this when a 401 is received from the API.
     * Clears the stored token and prompts the user to re-login — once, even when several
     * requests fail together, and only over a visible screen (a dialog can't be shown from
     * the application context, which used to crash here).
     */
    fun onUnauthorized(context: Context) {
        if (!TokenManager.isLoggedIn(context)) return
        TokenManager.clear(context)
        val activity = App.instance.currentActivity ?: return
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.auth_guard_session_expired_title))
            .setMessage(activity.getString(R.string.auth_guard_session_expired_message))
            .setPositiveButton(activity.getString(R.string.auth_guard_go_login)) { _, _ ->
                activity.startActivity(
                    Intent(activity, PhoneAuthActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            .setNegativeButton(activity.getString(R.string.action_cancel), null)
            .show()
    }
}
