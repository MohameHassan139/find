package com.example.myapplication.data.repository

import android.content.Context
import com.example.myapplication.auth.AuthResponse
import com.example.myapplication.auth.AuthUser
import com.example.myapplication.auth.DeviceTokenRequest
import com.example.myapplication.auth.OtpRequest
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.auth.VerifyOtpRequest
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService
import okhttp3.MultipartBody

/** Login (OTP), the signed-in user's profile, and account actions. */
class AuthRepository(
    private val api: FindApiService,
    private val appContext: Context
) {

    suspend fun requestOtp(phone: String): ApiResult<Unit> =
        apiCall { api.requestOtp(OtpRequest(phone)) }

    suspend fun verifyOtp(phone: String, code: String): ApiResult<AuthResponse?> =
        apiCall({ api.verifyOtp(VerifyOtpRequest(phone, code)) }) { it.body() }

    /** GET /auth/me; on success the cached user (name/phone/avatar/id) is refreshed too. */
    suspend fun fetchMe(): ApiResult<AuthUser?> =
        apiCall({ api.getMe() }) { it.body()?.user }.also { result ->
            (result as? ApiResult.Success)?.data?.let { TokenManager.updateUser(appContext, it) }
        }

    /** PATCH /user/profile; on success the cached user is refreshed too. */
    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<AuthUser?> =
        apiCall({ api.updateProfile(request) }) { it.body()?.user }.also { result ->
            (result as? ApiResult.Success)?.data?.let { TokenManager.updateUser(appContext, it) }
        }

    /** Uploads a new avatar and returns its URL (also saved to the cached user). */
    suspend fun uploadAvatar(image: MultipartBody.Part): ApiResult<String?> =
        apiCall({ api.uploadAvatar(image) }) { it.body()?.data?.url }.also { result ->
            (result as? ApiResult.Success)?.data?.takeIf { it.isNotEmpty() }
                ?.let { TokenManager.updateAvatar(appContext, it) }
        }

    suspend fun signOut(): ApiResult<Unit> = apiCall { api.signOut() }

    /** Returns the server's confirmation message. */
    suspend fun deleteAccount(): ApiResult<String?> =
        apiCall({ api.deleteAccount() }) { it.body()?.message }

    suspend fun registerDeviceToken(token: String): ApiResult<Unit> =
        apiCall { api.registerDeviceToken(DeviceTokenRequest(token)) }
}
