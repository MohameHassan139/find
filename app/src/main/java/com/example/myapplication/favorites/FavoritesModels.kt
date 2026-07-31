package com.example.myapplication.favorites

import com.google.gson.annotations.SerializedName

data class AddFavoriteRequest(
    @SerializedName("listing_id") val listingId: String
)
