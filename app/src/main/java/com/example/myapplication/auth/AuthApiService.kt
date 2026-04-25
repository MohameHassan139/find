package com.example.myapplication.auth

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {

    @POST("auth/request-otp")
    suspend fun requestOtp(@Body request: OtpRequest): Response<OtpResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<MeResponse>

    @PATCH("user/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<MeResponse>

    @POST("auth/sign-out")
    suspend fun signOut(@Header("Authorization") token: String): Response<Unit>

    @GET("listings")
    suspend fun getMyListings(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1
    ): Response<ListingsResponse>

    @DELETE("listings/{id}")
    suspend fun deleteListing(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @PATCH("listings/{id}")
    suspend fun toggleListing(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: ToggleActiveRequest
    ): Response<SingleListingResponse>

    @POST("auth/delete-account")
    suspend fun deleteAccount(@Header("Authorization") token: String): Response<DeleteAccountResponse>
}

data class OtpRequest(val phone: String)
data class OtpResponse(val message: String?)
data class VerifyOtpRequest(val phone: String, val code: String)

data class AuthResponse(
    val token: String?,
    val user: AuthUser?,
    val message: String?
)

data class MeResponse(val user: AuthUser?)

data class AuthUser(
    val id: Int = 0,
    val phone: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    @SerializedName("whatsapp_enabled") val whatsappEnabled: Boolean = false,
    @SerializedName("call_enabled") val callEnabled: Boolean = false
)

data class UpdateProfileRequest(
    val name: String,
    @SerializedName("whatsapp_enabled") val whatsappEnabled: Boolean = false,
    @SerializedName("call_enabled") val callEnabled: Boolean = false
)

data class ListingsResponse(
    val data: List<ListingItem>?,
    val message: String?,
    val status: Boolean?
)

data class SingleListingResponse(
    val data: ListingItem?,
    val message: String?,
    val status: Boolean?
)

data class ToggleActiveRequest(
    @SerializedName("is_active") val isActive: Boolean
)

data class ListingItem(
    val id: String,                                          // UUID string
    val title: String?,
    val price: Double?,                                      // number not string
    val description: String? = null,
    @SerializedName("listing_type") val listingType: String?,
    @SerializedName("is_active") val isActive: Boolean = true,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String?,
    val images: List<String>?,                               // direct URL strings
    val seller: SellerInfo? = null,
    val region: RegionInfo? = null,
    val city: String? = null,
    @SerializedName("region_id") val regionId: Int? = null,
    @SerializedName("category_id") val categoryId: Int? = null,
    @SerializedName("sub_category_id") val subCategoryId: Int? = null
)

data class SellerInfo(val id: Int = 0, val name: String?, val avatar: String?)
data class RegionInfo(val id: Int = 0, @SerializedName("name_ar") val nameAr: String?)

data class DeleteAccountResponse(val message: String?)
