package com.example.myapplication.auth

import com.google.gson.annotations.SerializedName

data class OtpRequest(val phone: String)
data class OtpResponse(val message: String?)
data class VerifyOtpRequest(val phone: String, val code: String)

// Matches the iOS client's contract exactly (Core/Network/APIClient.swift
// registerDeviceToken) — same endpoint, same single field, for both platforms.
data class DeviceTokenRequest(@SerializedName("device_token") val deviceToken: String)

// verify-otp nests token/user under "data" (every backend envelope does —
// see docs/API_REFERENCE.md "Error shape"/response examples). `message`
// stays top-level.
data class AuthResponse(
    val data: AuthResponseData?,
    val message: String?
) {
    val token: String? get() = data?.token
    val user:  AuthUser? get() = data?.user
}

data class AuthResponseData(
    val token: String?,
    val user: AuthUser?
)

data class MeResponse(val data: MeResponseData?) {
    val user: AuthUser? get() = data?.user
}

data class MeResponseData(val user: AuthUser?)

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

// GET /my-listings nests items+pagination under "data" (see
// docs/API_REFERENCE.md § GET /my-listings) — not a bare array.
data class ListingsResponse(
    val data: MyListingsData?,
    val message: String?
)

data class MyListingsData(
    val items: List<ListingItem>?,
    val pagination: Pagination?
)

data class Pagination(
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("last_page") val lastPage: Int = 1,
    val total: Int = 0
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

data class UploadAvatarResponse(val data: UploadAvatarData?, val message: String?)

data class UploadAvatarData(
    val url: String?,
    @SerializedName("avatar_thumb_url") val avatarThumbUrl: String?
)