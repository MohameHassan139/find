package com.example.myapplication.favorites

import com.example.myapplication.auth.ListingItem
import com.example.myapplication.auth.Pagination
import com.google.gson.annotations.SerializedName

data class AddFavoriteRequest(
    @SerializedName("listing_id") val listingId: String
)

/**
 * `GET /favorites` → `{ "data": { "items": [...], "pagination": {...} } }`.
 *
 * Typed on purpose. Every caller used to hand-parse this with
 * `JSONObject(body).optJSONArray("data")`, which silently returns null because
 * `data` is an object, not an array — so the favorites screen was permanently
 * empty and no heart ever rendered filled. Same class of bug as the search one
 * fixed in 186a09d; making the shape explicit stops it recurring.
 */
data class FavoritesResponse(
    val data: FavoritesData?,
    val message: String? = null
)

data class FavoritesData(
    val items: List<ListingItem>? = null,
    val pagination: Pagination? = null
)

/**
 * `GET /listings/{id}/is-favorited` → `{ "data": { "is_favorited": true } }`.
 *
 * Note the nesting: the flag is under `data`, not at the top level.
 */
data class IsFavoritedResponse(
    val data: IsFavoritedData?
) {
    val isFavorited: Boolean get() = data?.isFavorited ?: false
}

data class IsFavoritedData(
    @SerializedName("is_favorited") val isFavorited: Boolean = false
)
