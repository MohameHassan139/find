package com.example.myapplication.models

import com.google.gson.annotations.SerializedName

data class ListingsApiResponse(
    val data: List<Listing>?,
    val meta: ListingsMeta?
)

data class ListingsMeta(
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("last_page") val lastPage: Int = 1,
    val total: Int = 0
)

data class Listing(
    val id: String,
    val title: String?,
    val price: Double?,
    val description: String?,
    @SerializedName("listing_type") val listingType: String?,
    val status: String?,
    @SerializedName("created_at") val createdAt: String?,
    val images: List<String>?,
    val seller: ListingSeller?,
    val region: ListingRegion?,
    val city: String?,
    @SerializedName("category_id") val categoryId: Int?,
    @SerializedName("sub_category_id") val subCategoryId: Int?,
    @SerializedName("region_id") val regionId: Int?
)

data class ListingSeller(
    val id: Int = 0,
    val name: String?,
    val avatar: String?
)

data class ListingRegion(
    val id: Int = 0,
    @SerializedName("name_ar") val nameAr: String?
)
