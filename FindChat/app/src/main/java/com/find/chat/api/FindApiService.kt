package com.find.chat.api

import com.find.chat.model.*
import retrofit2.Response
import retrofit2.http.*

interface FindApiService {

    @GET("conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String = "Bearer AbCd1234efgh5678ijkl90MnOpQrStUvWxYzAbCdEfGhIjKl"
    ): Response<ConversationsResponse>

    @POST("conversations")
    suspend fun createConversation(
        @Header("Authorization") token: String = "Bearer AbCd1234efgh5678ijkl90MnOpQrStUvWxYzAbCdEfGhIjKl",
        @Body request: CreateConversationRequest
    ): Response<CreateConversationResponse>

    @GET("conversations/{id}/messages")
    suspend fun getMessages(
        @Path("id") conversationId: Int,
        @Header("Authorization") token: String = "Bearer AbCd1234efgh5678ijkl90MnOpQrStUvWxYzAbCdEfGhIjKl"
    ): Response<MessagesResponse>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: Int,
        @Header("Authorization") token: String = "Bearer AbCd1234efgh5678ijkl90MnOpQrStUvWxYzAbCdEfGhIjKl",
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @PATCH("conversations/{id}/read")
    suspend fun markRead(
        @Path("id") conversationId: Int,
        @Header("Authorization") token: String = "Bearer AbCd1234efgh5678ijkl90MnOpQrStUvWxYzAbCdEfGhIjKl"
    ): Response<Unit>
}
