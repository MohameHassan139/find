package com.example.myapplication.push

import android.content.Context
import com.example.myapplication.auth.DeviceTokenRequest
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Mirrors the iOS client's device-token flow (AuthService.saveDeviceToken /
 * uploadStoredDeviceTokenIfNeeded): cache the latest FCM token locally, and only push it
 * to the backend once the user is logged in. All Firebase calls are best-effort — push is
 * a non-critical enhancement, so failures (including "Firebase isn't set up yet" before
 * google-services.json is added) are swallowed rather than surfaced to the user.
 */
object PushTokenManager {
    private const val PREFS = "find_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    // Detached scope for call sites (e.g. right before an Activity finishes/navigates
    // away) where tying the upload to that Activity's lifecycleScope would risk
    // cancelling it mid-flight.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget variant of [refreshAndUploadIfNeeded] that survives the caller finishing. */
    fun refreshAndUploadIfNeededAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refreshAndUploadIfNeeded(appContext) }
    }

    fun cacheToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    private fun getCachedToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FCM_TOKEN, null)

    /** Fetches the current token (refreshing the cache) and uploads it if logged in. */
    suspend fun refreshAndUploadIfNeeded(context: Context) {
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        } ?: return
        cacheToken(context, token)
        uploadIfNeeded(context)
    }

    /** Uploads the last-known token if the user is logged in. No-ops otherwise. */
    suspend fun uploadIfNeeded(context: Context) {
        if (!TokenManager.isLoggedIn(context)) return
        val token = getCachedToken(context) ?: return
        try {
            RetrofitClient.build(context).registerDeviceToken(DeviceTokenRequest(token))
        } catch (e: Exception) {
            // Non-critical — retried on next launch or token refresh.
        }
    }
}