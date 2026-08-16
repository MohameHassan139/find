package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PAGE_SIZE = 20

class SearchViewModel : ViewModel() {

    enum class State { IDLE, LOADING, RESULTS, EMPTY }

    private val _bodyState = MutableLiveData<State>(State.IDLE)
    val bodyState: LiveData<State> get() = _bodyState

    private val _results = MutableLiveData<List<ApiListing>>()
    val results: LiveData<List<ApiListing>> get() = _results

    private val _isPagingLoading = MutableLiveData<Boolean>(false)
    val isPagingLoading: LiveData<Boolean> get() = _isPagingLoading

    private val _currentQuery = MutableLiveData<String?>()
    val currentQuery: LiveData<String?> get() = _currentQuery

    private val _regions = MutableLiveData<List<RegionItem>>()
    val regions: LiveData<List<RegionItem>> get() = _regions

    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> get() = _errorEvent

    // Filter state
    private var activeQuery: String = ""
    private var activeType: String? = null
    private var activeRegionId: Int? = null

    // Pagination
    private var currentPage = 1
    private var lastPage = 1
    private var isFetching = false

    init { loadRegions() }

    private fun loadRegions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getAppData()
                if (response.isSuccessful) {
                    val root = JSONObject(response.body()?.string() ?: "")
                    val data = root.optJSONObject("data") ?: root
                    val arr = data.optJSONArray("regions") ?: JSONArray()
                    val regions = mutableListOf<RegionItem>()
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        regions.add(RegionItem(r.getInt("id"), r.getString("name_ar")))
                    }
                    withContext(Dispatchers.Main) { _regions.value = regions }
                }
            } catch (_: Exception) {}
        }
    }

    fun search(query: String, reset: Boolean = true) {
        if (query.isBlank()) { clearSearch(); return }
        activeQuery = query.trim()
        _currentQuery.value = activeQuery
        fetchResults(reset = reset)
    }

    fun clearSearch() {
        activeQuery = ""
        _currentQuery.value = null
        _results.value = emptyList()
        _bodyState.value = State.IDLE
        currentPage = 1
        lastPage = 1
    }

    fun selectType(type: String?) {
        activeType = type
        if (activeQuery.isNotBlank()) fetchResults(reset = true)
    }

    fun selectRegion(regionId: Int?) {
        activeRegionId = regionId
        if (activeQuery.isNotBlank()) fetchResults(reset = true)
    }

    fun loadNextPage() {
        if (!isFetching && currentPage < lastPage) fetchResults(reset = false)
    }

    fun hasMorePages() = currentPage < lastPage

    private fun fetchResults(reset: Boolean) {
        if (activeQuery.isBlank()) return
        if (isFetching) return

        if (reset) {
            currentPage = 1
            _results.value = emptyList()
            _bodyState.value = State.LOADING
        } else {
            currentPage++
            _isPagingLoading.value = true
        }

        val page = currentPage

        isFetching = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.searchListings(
                    query = activeQuery,
                    page = page,
                    limit = PAGE_SIZE,
                    regionId = activeRegionId,
                    listingType = activeType
                )
                if (response.isSuccessful) {
                    val body = JSONObject(response.body()?.string() ?: "")
                    // GET /listings wraps results as data: { items: [...], pagination: {...} }
                    // (matches ListingsService.swift's DataObj on iOS) — data itself is an
                    // object, not the array directly. This was the actual search-returns-
                    // nothing bug: optJSONArray("data") silently returns empty since "data"
                    // is a JSON object here, not an array.
                    val data = body.optJSONObject("data")
                    val arr = data?.optJSONArray("items") ?: JSONArray()
                    val pagination = data?.optJSONObject("pagination")
                    val fetchedLastPage = pagination?.optInt("last_page", 1) ?: 1
                    val fetched = parseListings(arr)
                    withContext(Dispatchers.Main) {
                        lastPage = fetchedLastPage
                        val current = if (reset) emptyList() else (_results.value ?: emptyList())
                        val combined = current + fetched
                        _results.value = combined
                        _isPagingLoading.value = false
                        _bodyState.value = if (combined.isEmpty()) State.EMPTY else State.RESULTS
                        isFetching = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // Roll the page counter back so the next scroll retries
                        // this page rather than skipping it.
                        if (!reset) currentPage--
                        _isPagingLoading.value = false
                        _bodyState.value = if (reset) State.EMPTY else State.RESULTS
                        isFetching = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!reset) currentPage--
                    _isPagingLoading.value = false
                    _bodyState.value = if (reset) State.EMPTY else State.RESULTS
                    _errorEvent.value = "تعذر البحث"
                    isFetching = false
                }
            }
        }
    }

    private fun parseListings(arr: JSONArray): List<ApiListing> {
        val list = mutableListOf<ApiListing>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val images = mutableListOf<String>()
            val imgArr = o.optJSONArray("images")
            if (imgArr != null) for (j in 0 until imgArr.length()) images.add(imgArr.getString(j))
            val seller = o.optJSONObject("seller")
            val region = o.optJSONObject("region")
            list.add(ApiListing(
                id = o.optString("id"),
                title = o.optString("title").ifEmpty { null },
                price = if (!o.isNull("price")) o.optDouble("price") else null,
                listingType = o.optString("listing_type").ifEmpty { null },
                createdAt = o.optString("created_at").ifEmpty { null },
                images = images,
                sellerName = seller?.optString("name")?.ifEmpty { null },
                sellerAvatar = seller?.optString("avatar")?.ifEmpty { null },
                regionNameAr = region?.optString("name_ar")?.ifEmpty { null },
                city = o.optString("city").ifEmpty { null }
            ))
        }
        return list
    }
}
