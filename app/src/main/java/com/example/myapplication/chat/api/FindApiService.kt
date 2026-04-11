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

    @GET("listings")
    suspend fun getListingsCombined(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("category_id") categoryId: Int,
        @Query("sub_category_id") subCategoryId: Int? = null,
        @Query("filter_option_id") filterOptionId: Int? = null,
        @Query("region_id") regionId: Int? = null,
        @Query("city") city: String? = null,
        @Query("listing_type") listingType: String? = null
    ): Response<okhttp3.ResponseBody>

    @GET("categories")
    suspend fun getCategories(): Response<okhttp3.ResponseBody>

    @GET("categories/{id}")
    suspend fun getCategoryDetails(@Path("id") id: Int): Response<okhttp3.ResponseBody>

    @GET("search-filters")
    suspend fun getSearchFilters(): Response<okhttp3.ResponseBody>

    // ── Favorites ─────────────────────────────────────────────────────────────

    @GET("favorites")
    suspend fun getFavorites(@Query("page") page: Int = 1): Response<okhttp3.ResponseBody>

    @POST("favorites")
    suspend fun addFavorite(@Body body: com.example.myapplication.favorites.AddFavoriteRequest): Response<okhttp3.ResponseBody>

    @DELETE("favorites/{listing_id}")
    suspend fun removeFavorite(@Path("listing_id") listingId: String): Response<okhttp3.ResponseBody>

    @GET("listings/{id}/is-favorited")
    suspend fun isFavorited(@Path("id") listingId: String): Response<okhttp3.ResponseBody>
}
