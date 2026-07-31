package com.example.myapplication.chat.api

import com.example.myapplication.auth.AuthResponse
import com.example.myapplication.auth.DeleteAccountResponse
import com.example.myapplication.auth.DeviceTokenRequest
import com.example.myapplication.auth.ListingsResponse
import com.example.myapplication.auth.MeResponse
import com.example.myapplication.auth.OtpRequest
import com.example.myapplication.auth.OtpResponse
import com.example.myapplication.auth.SingleListingResponse
import com.example.myapplication.auth.ToggleActiveRequest
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.auth.UploadAvatarResponse
import com.example.myapplication.auth.VerifyOtpRequest
import com.example.myapplication.chat.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface FindApiService {

    // ── Auth / profile (token auto-attached by RetrofitClient's interceptor,
    // no manual Authorization header needed — see RetrofitClient.build) ───────

    @POST("auth/request-otp")
    suspend fun requestOtp(@Body request: OtpRequest): Response<OtpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<MeResponse>

    @PATCH("user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<MeResponse>

    @POST("auth/sign-out")
    suspend fun signOut(): Response<Unit>

    @POST("auth/delete-account")
    suspend fun deleteAccount(): Response<DeleteAccountResponse>

    @Multipart
    @POST("upload-avatar")
    suspend fun uploadAvatar(@Part image: MultipartBody.Part): Response<UploadAvatarResponse>

    // Same contract as the iOS client — see AuthModels.DeviceTokenRequest.
    @POST("device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Response<Unit>

    @GET("my-listings")
    suspend fun getMyListings(
        @Query("page") page: Int = 1,
        @Query("status") status: String? = null
    ): Response<ListingsResponse>

    @DELETE("listings/{id}")
    suspend fun deleteListing(@Path("id") id: String): Response<Unit>

    @PATCH("listings/{id}")
    suspend fun toggleListing(
        @Path("id") id: String,
        @Body body: ToggleActiveRequest
    ): Response<SingleListingResponse>

    @GET("conversations")
    suspend fun getConversations(): Response<ConversationsResponse>

    // Lightweight poll target — intended to be hit every ~5s instead of
    // re-fetching full conversations/messages. See docs/API_REFERENCE.md.
    @GET("conversations/updates")
    suspend fun getConversationUpdates(): Response<ConversationUpdatesResponse>

    @POST("conversations")
    suspend fun createConversation(@Body request: CreateConversationRequest): Response<CreateConversationResponse>

    // No params returns the newest `limit` messages (oldest-first), not full
    // history — page older with `before`, or poll new ones with `after`.
    @GET("conversations/{id}/messages")
    suspend fun getMessages(
        @Path("id") conversationId: String,
        @Query("limit") limit: Int? = null,
        @Query("before") before: String? = null,
        @Query("after") after: String? = null
    ): Response<MessagesResponse>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @PATCH("conversations/{id}/read")
    suspend fun markRead(@Path("id") conversationId: String): Response<Unit>

    // GET /notifications has no route server-side — notifications + their
    // unread count are served from this single bootstrap call instead.
    @GET("user-data")
    suspend fun getUserData(): Response<UserDataResponse>

    @POST("notifications/mark-all-read")
    suspend fun markAllNotificationsRead(): Response<Unit>

    @GET("listings")
    suspend fun getListingsCombined(
        @Query("page") page: Int,
        @Query("limit") perPage: Int,
        @Query("category_id") categoryId: Int,
        @Query("sub_category_id") subCategoryId: Int? = null,
        @Query("filter_option_id") filterOptionId: Int? = null,
        @Query("region_id") regionId: Int? = null,
        @Query("city") city: String? = null,
        @Query("listing_type") listingType: String? = null
    ): Response<okhttp3.ResponseBody>

    // Single public bootstrap call — listings + categories (with nested
    // sub_categories/filter_options) + regions (with nested cities) + config.
    // Replaces the old (nonexistent on the backend) GET categories,
    // GET categories/{id} and GET search-filters calls.
    @GET("app-data")
    suspend fun getAppData(): Response<okhttp3.ResponseBody>

    @GET("listings")
    suspend fun searchListings(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("region_id") regionId: Int? = null,
        @Query("listing_type") listingType: String? = null
    ): Response<okhttp3.ResponseBody>

    @GET("listings/{id}")
    suspend fun getListingDetail(@Path("id") id: String): Response<okhttp3.ResponseBody>

    // Body is a pre-built JSON RequestBody (rather than a typed model) since
    // sub_category_id/filter_option_id must be omitted entirely when unset —
    // sending them as JSON null would still trip the backend's `sometimes`
    // validation rule (present-but-null still counts as "present").
    @POST("listings")
    suspend fun createListing(@Body body: okhttp3.RequestBody): Response<okhttp3.ResponseBody>

    @PATCH("listings/{id}")
    suspend fun updateListingFull(
        @Path("id") id: String,
        @Body body: okhttp3.RequestBody
    ): Response<okhttp3.ResponseBody>

    @Multipart
    @POST("upload-image")
    suspend fun uploadListingImage(
        @Part listingId: MultipartBody.Part,
        @Part image: MultipartBody.Part
    ): Response<okhttp3.ResponseBody>

    // ── Favorites ─────────────────────────────────────────────────────────────

    @GET("favorites")
    suspend fun getFavorites(@Query("page") page: Int = 1): Response<okhttp3.ResponseBody>

    @POST("favorites")
    suspend fun addFavorite(@Body body: com.example.myapplication.favorites.AddFavoriteRequest): Response<okhttp3.ResponseBody>

    @DELETE("favorites/{listing_id}")
    suspend fun removeFavorite(@Path("listing_id") listingId: String): Response<okhttp3.ResponseBody>

    @GET("listings/{id}/is-favorited")
    suspend fun isFavorited(@Path("id") listingId: String): Response<okhttp3.ResponseBody>

    // ── Moderation (App Store/Play Store content-moderation requirement) ───────

    @GET("blocks")
    suspend fun getBlocks(): Response<com.example.myapplication.chat.model.BlocksResponse>

    @POST("blocks")
    suspend fun blockUser(
        @Body body: com.example.myapplication.chat.model.BlockUserRequest
    ): Response<com.example.myapplication.chat.model.BlockResponse>

    @DELETE("blocks/{user_id}")
    suspend fun unblockUser(@Path("user_id") userId: Int): Response<Unit>

    @POST("reports")
    suspend fun report(
        @Body body: com.example.myapplication.chat.model.ReportRequest
    ): Response<Unit>
}
