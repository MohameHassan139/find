package com.example.myapplication.data.repository

import com.example.myapplication.DetailListing
import com.example.myapplication.auth.ListingItem
import com.example.myapplication.auth.UpdateStatusRequest
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.ListingJson
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** A single ad (details) and the signed-in user's own ads: create, edit, hide, delete. */
class ListingRepository(private val api: FindApiService) {

    suspend fun detail(id: String): ApiResult<DetailListing?> =
        apiCall({ api.getListingDetail(id) }) { response ->
            JSONObject(response.body()?.string().orEmpty()).optJSONObject("data")?.let(ListingJson::parseDetail)
        }

    /** Every page of GET /my-listings (capped at [MAX_PAGES]). Fails if any page fails. */
    suspend fun myListings(): ApiResult<List<ListingItem>> {
        val collected = mutableListOf<ListingItem>()
        var page = 1
        var lastPage = 1
        do {
            val result = apiCall({ api.getMyListings(page = page) }) { it.body()?.data }
            val data = when (result) {
                is ApiResult.Success -> result.data
                is ApiResult.HttpError -> return result
                is ApiResult.NetworkError -> return result
            }
            collected += data?.items.orEmpty()
            lastPage = data?.pagination?.lastPage ?: 1
            page++
        } while (page <= lastPage && page <= MAX_PAGES)
        return ApiResult.Success(collected)
    }

    suspend fun delete(id: String): ApiResult<Unit> = apiCall { api.deleteListing(id) }

    /** Hide/unhide — PATCHes only `{"status": ...}`, never `images`. */
    suspend fun setVisible(id: String, visible: Boolean): ApiResult<Unit> =
        apiCall { api.setListingStatus(id, UpdateStatusRequest.forVisible(visible)) }

    /** POST /listings; returns the new listing's id. */
    suspend fun create(body: JSONObject): ApiResult<String?> =
        apiCall({ api.createListing(body.toJsonBody()) }) { response ->
            JSONObject(response.body()?.string().orEmpty())
                .optJSONObject("data")?.optString("id")?.takeIf { it.isNotEmpty() }
        }

    suspend fun update(id: String, body: JSONObject): ApiResult<Unit> =
        apiCall { api.updateListingFull(id, body.toJsonBody()) }

    /** Uploads one image to a listing; returns its URL (nested under `data.url`). */
    suspend fun uploadImage(listingId: String, bytes: ByteArray, mimeType: String): ApiResult<String?> =
        apiCall({
            api.uploadListingImage(
                MultipartBody.Part.createFormData("listing_id", listingId),
                MultipartBody.Part.createFormData(
                    "image", "img_${System.currentTimeMillis()}.jpg",
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
            )
        }) { response ->
            JSONObject(response.body()?.string().orEmpty())
                .optJSONObject("data")?.optString("url")?.takeIf { it.isNotEmpty() }
        }

    private fun JSONObject.toJsonBody() = toString().toRequestBody("application/json".toMediaTypeOrNull())

    private companion object {
        const val MAX_PAGES = 20
    }
}
