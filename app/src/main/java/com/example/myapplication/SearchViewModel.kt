package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

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
        viewModelScope.launch {
            val data = AppContainer.catalog.appData().getOrNull() ?: return@launch
            val arr = data.optJSONArray("regions") ?: return@launch
            _regions.value = (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                RegionItem(r.optInt("id"), r.optString("name_ar"))
            }
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
        viewModelScope.launch {
            val result = AppContainer.catalog.search(
                query = activeQuery,
                page = page,
                limit = PAGE_SIZE,
                regionId = activeRegionId,
                listingType = activeType
            )
            if (result is ApiResult.Success) {
                lastPage = result.data.lastPage
                val current = if (reset) emptyList() else (_results.value ?: emptyList())
                val combined = current + result.data.items
                _results.value = combined
                _bodyState.value = if (combined.isEmpty()) State.EMPTY else State.RESULTS
            } else {
                // Roll the page counter back so the next scroll retries
                // this page rather than skipping it.
                if (!reset) currentPage--
                _bodyState.value = if (reset) State.EMPTY else State.RESULTS
                // A plain HTTP error just shows "no results"; no answer / unreadable body
                // also shows a message.
                val silent = result is ApiResult.HttpError && result.code != ApiResult.UNREADABLE_BODY
                if (!silent) _errorEvent.value = "تعذر البحث"
            }
            _isPagingLoading.value = false
            isFetching = false
        }
    }
}
