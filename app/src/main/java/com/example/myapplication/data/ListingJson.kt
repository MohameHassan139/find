package com.example.myapplication.data

import com.example.myapplication.ApiListing
import org.json.JSONArray
import org.json.JSONObject
import com.example.myapplication.auth.ListingItem
import com.example.myapplication.DetailListing

/**
 * The single mapper from a listing JSON object to the app's [ApiListing] display model.
 * Used by the home feed, search and the ad-details sibling list, which previously each
 * carried their own slightly different copy of this code.
 */
object ListingJson {

    fun parseList(arr: JSONArray?): List<ApiListing> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parse) }
    }

    /** GET /listings/{id} `data` → the ad-details model (adds seller contact fields). */
    fun parseDetail(o: JSONObject): DetailListing {
        val seller = o.optJSONObject("seller")
        val regionObj = o.optJSONObject("region")
        return DetailListing(
            id = o.optString("id"),
            title = o.stringOrNull("title"),
            description = o.stringOrNull("description"),
            price = o.doubleOrNull("price"),
            listingType = o.stringOrNull("listing_type"),
            createdAt = o.stringOrNull("created_at"),
            images = o.stringList("images"),
            sellerName = seller?.stringOrNull("name"),
            sellerAvatar = seller?.stringOrNull("avatar"),
            sellerPhone = seller?.stringOrNull("phone"),
            sellerId = seller?.optInt("id"),
            whatsappEnabled = seller?.optBoolean("whatsapp_enabled") ?: false,
            callEnabled = seller?.optBoolean("call_enabled") ?: false,
            regionNameAr = regionObj?.stringOrNull("name_ar") ?: o.stringOrNull("region_name") ?: o.stringOrNull("region"),
            city = o.cityName()
        )
    }

    fun parse(o: JSONObject): ApiListing {
        val seller = o.optJSONObject("seller")
        val regionObj = o.optJSONObject("region")
        return ApiListing(
            id = o.optString("id"),
            title = o.stringOrNull("title"),
            price = o.doubleOrNull("price"),
            listingType = o.stringOrNull("listing_type") ?: o.stringOrNull("type"),
            createdAt = o.stringOrNull("created_at"),
            images = o.stringList("images"),
            sellerName = seller?.stringOrNull("name"),
            sellerAvatar = seller?.stringOrNull("avatar"),
            regionNameAr = regionObj?.stringOrNull("name_ar") ?: o.stringOrNull("region_name") ?: o.stringOrNull("region"),
            city = o.cityName(),
            categoryId = o.intOrNull("category_id"),
            subCategoryId = o.intOrNull("sub_category_id"),
            filterOptionId = o.intOrNull("filter_option_id"),
            regionId = o.intOrNull("region_id") ?: regionObj?.intOrNull("id"),
            description = o.stringOrNull("description")
        )
    }
}

/** Typed (Retrofit/Gson) listing → the same display model, e.g. for the favorites list. */
fun ListingItem.toApiListing() = ApiListing(
    id = id,
    title = title?.takeIf { it.isNotEmpty() },
    price = price,
    listingType = listingType?.takeIf { it.isNotEmpty() },
    createdAt = createdAt?.takeIf { it.isNotEmpty() },
    images = images.orEmpty(),
    sellerName = seller?.name?.takeIf { it.isNotEmpty() },
    sellerAvatar = seller?.avatar?.takeIf { it.isNotEmpty() },
    regionNameAr = region?.nameAr?.takeIf { it.isNotEmpty() },
    city = city?.takeIf { it.isNotEmpty() },
    regionId = regionId ?: region?.id?.takeIf { it > 0 }
)
