package com.example.myapplication.utils

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.R
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager

object AuthGuard {

    /**
     * Checks if the user is logged in.
     * If yes, runs [action] immediately.
     * If no, shows a dialog asking the user to log in or cancel.
     */
    fun requireLogin(context: Context, action: () -> Unit) {
        if (TokenManager.isLoggedIn(context)) {
            action()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.auth_guard_title))
            .setMessage(context.getString(R.string.auth_guard_message))
            .setPositiveButton(context.getString(R.string.auth_guard_go_login)) { _, _ ->
                context.startActivity(Intent(context, PhoneAuthActivity::class.java))
            }
            .setNegativeButton(context.getString(R.string.auth_guard_cancel), null)
            .show()
    }

    /**
     * Call this when a 401 is received from the API.
     * Clears the stored token and prompts the user to re-login.
     */
    fun onUnauthorized(context: Context) {
        TokenManager.clear(context)
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.auth_guard_session_expired_title))
            .setMessage(context.getString(R.string.auth_guard_session_expired_message))
            .setPositiveButton(context.getString(R.string.auth_guard_go_login)) { _, _ ->
                context.startActivity(
                    Intent(context, PhoneAuthActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            .setNegativeButton(context.getString(R.string.auth_guard_cancel), null)
            .show()
    }
}
