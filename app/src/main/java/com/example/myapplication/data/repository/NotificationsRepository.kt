package com.example.myapplication.data.repository

import com.example.myapplication.chat.model.UserDataPayload
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService

/** Notifications and their unread count (served by GET /user-data). */
class NotificationsRepository(private val api: FindApiService) {

    suspend fun userData(): ApiResult<UserDataPayload?> =
        apiCall({ api.getUserData() }) { it.body()?.data }

    /** Unread notifications; 0 when unknown. */
    suspend fun unreadCount(): ApiResult<Int> =
        apiCall({ api.getUserData() }) { response ->
            val payload = response.body()?.data
            payload?.unreadNotifications ?: payload?.notifications?.count { !it.isRead } ?: 0
        }

    suspend fun markAllRead(): ApiResult<Unit> = apiCall { api.markAllNotificationsRead() }
}
