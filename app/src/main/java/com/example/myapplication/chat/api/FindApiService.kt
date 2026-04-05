package com.example.myapplication.chat.api

import com.example.myapplication.chat.model.*
import retrofit2.Response
import retrofit2.http.*

interface FindApiService {

    @GET("conversations")
    suspend fun getConversations(): Response<ConversationsResponse>

    @POST("conversations")
    suspend fun createConversation(@Body request: CreateConversationRequest): Response<CreateConversationResponse>

    @GET("conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): Response<MessagesResponse>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @PATCH("conversations/{id}/read")
    suspend fun markRead(@Path("id") conversationId: String): Response<Unit>

    @GET("notifications")
    suspend fun getNotifications(@Query("page") page: Int = 1): Response<NotificationsResponse>

    @POST("notifications/mark-all-read")
    suspend fun markAllNotificationsRead(): Response<Unit>
}
