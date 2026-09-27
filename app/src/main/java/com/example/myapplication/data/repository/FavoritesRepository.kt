package com.example.myapplication.data.repository

import com.example.myapplication.ApiListing
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService
import com.example.myapplication.data.toApiListing
import com.example.myapplication.favorites.AddFavoriteRequest

/** The signed-in user's saved ads. */
class FavoritesRepository(private val api: FindApiService) {

    /**
     * Every favorite, walking all pages of GET /favorites (data.items + data.pagination,
     * 15 per page by default server-side). Fails only if the first page fails.
     */
    suspend fun all(): ApiResult<List<ApiListing>> {
        val collected = mutableListOf<ApiListing>()
        var page = 1
        var lastPage = 1
        do {
            val result = apiCall({ api.getFavorites(page = page) }) { it.body()?.data }
            val data = when (result) {
                is ApiResult.Success -> result.data ?: break
                is ApiResult.HttpError -> if (page == 1) return result else break
                is ApiResult.NetworkError -> if (page == 1) return result else break
            }
            collected += data.items.orEmpty().map { it.toApiListing() }
            lastPage = data.pagination?.lastPage ?: 1
            page++
        } while (page <= lastPage && page <= MAX_PAGES)
        return ApiResult.Success(collected)
    }

    /** Just the ids, e.g. to fill the bookmark icons on the home feed. */
    suspend fun ids(): Set<String> =
        all().getOrNull().orEmpty().map { it.id }.filter { it.isNotEmpty() }.toSet()

    suspend fun isFavorited(listingId: String): ApiResult<Boolean> =
        apiCall({ api.isFavorited(listingId) }) { it.body()?.isFavorited ?: false }

    suspend fun setFavorite(listingId: String, favorite: Boolean): ApiResult<Unit> =
        if (favorite) apiCall { api.addFavorite(AddFavoriteRequest(listingId)) }
        else apiCall { api.removeFavorite(listingId) }

    private companion object {
        const val MAX_PAGES = 20
    }
}
