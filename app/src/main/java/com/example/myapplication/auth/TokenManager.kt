package com.example.myapplication.auth

import android.content.Context
import com.example.myapplication.crash.CrashReporting
import com.example.myapplication.security.SecureTokenStore
import com.example.myapplication.utils.ModerationState

object TokenManager {
    private const val PREFS = "find_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_NAME = "user_name"
    private const val KEY_PHONE = "user_phone"
    private const val KEY_AVATAR = "user_avatar"
    private const val KEY_USER_ID = "user_id"

    fun save(context: Context, token: String, name: String = "", phone: String = "", avatar: String = "", userId: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, SecureTokenStore.encrypt(token))
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putString(KEY_AVATAR, avatar)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun getUserId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_ID, "") ?: ""

    // The bearer token is the one field here that actually grants API access, so it's the
    // only one worth encrypting at rest (see SecureTokenStore) — name/phone/avatar are just
    // display cache, not credentials.
    fun getToken(context: Context): String? {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null) ?: return null
        return SecureTokenStore.decrypt(encrypted)
    }

    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun getPhone(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, "") ?: ""

    fun getAvatar(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AVATAR, "") ?: ""

    fun isLoggedIn(context: Context) = getToken(context) != null

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        // Per-account state that must not leak into the next session on a
        // shared device (mirrors the other clients' logout handling).
        ModerationState.reset()
        CrashReporting.clearUserId()
    }
}
