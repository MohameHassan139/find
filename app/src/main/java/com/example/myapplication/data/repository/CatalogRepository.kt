package com.example.myapplication.data.repository

import com.example.myapplication.ApiListing
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.ListingJson
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One page of listings plus how many pages exist. */
data class ListingPage(val items: List<ApiListing>, val lastPage: Int)

/**
 * Public catalog: the app-data bootstrap, the listings feed and search (no token sent).
 * Responses can be large, so reading and parsing happen on the IO dispatcher.
 */
class CatalogRepository(private val api: FindApiService) {

    /** GET /app-data → its `data` object (listings, categories, regions, config). */
    suspend fun appData(): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        apiCall({ api.getAppData() }) { response ->
            val root = JSONObject(response.body()?.string().orEmpty())
            root.optJSONObject("data") ?: root
        }
    }

    suspend fun listings(
        page: Int,
        perPage: Int,
        categoryId: Int? = null,
        subCategoryId: Int? = null,
        filterOptionId: Int? = null,
        regionId: Int? = null,
        city: String? = null,
        listingType: String? = null
    ): ApiResult<ListingPage> = withContext(Dispatchers.IO) {
        apiCall({
            api.getListingsCombined(page, perPage, categoryId, subCategoryId, filterOptionId, regionId, city, listingType)
        }) { parsePage(it.body()?.string()) }
    }

    suspend fun search(
        query: String,
        page: Int,
        limit: Int,
        regionId: Int? = null,
        listingType: String? = null
    ): ApiResult<ListingPage> = withContext(Dispatchers.IO) {
        apiCall({ api.searchListings(query, page, limit, regionId, listingType) }) { parsePage(it.body()?.string()) }
    }

    /** GET /listings wraps results as `data: { items: [...], pagination: {...} }`. */
    private fun parsePage(body: String?): ListingPage {
        val data = JSONObject(body.orEmpty()).optJSONObject("data")
        return ListingPage(
            items = ListingJson.parseList(data?.optJSONArray("items")),
            lastPage = data?.optJSONObject("pagination")?.optInt("last_page", 1) ?: 1
        )
    }
}
