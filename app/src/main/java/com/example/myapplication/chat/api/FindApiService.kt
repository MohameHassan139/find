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
    suspend fun getMessages(@Path("id") conversationId: Int): Response<MessagesResponse>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: Int,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @PATCH("conversations/{id}/read")
    suspend fun markRead(@Path("id") conversationId: Int): Response<Unit>
}
