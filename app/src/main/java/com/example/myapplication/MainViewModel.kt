package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.models.toApiListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoderutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val ICON_BASE = "https://ocebfvgwgpebjxetnixc.supabase.co/storage/v1/object/public/listings-images/subCatigory/"
private const val PAGE_SIZE = 20

// ── Data models ───────────────────────────────────────────────────────────────

data class ApiCategory(
    val id: Int,
    val nameAr: String,
    val iconName: String? = null,
    val subCategories: List<ApiSubCategory> = emptyList()
) {
    val iconUrl: String? get() = iconName?.let { "$ICON_BASE$it.png" }
}

data class ApiSubCategory(
    val id: Int,
    val nameAr: String,
    val iconName: String? = null,
    val filterOptions: List<ApiFilterOption> = emptyList()
) {
    val iconUrl: String? get() = iconName?.let { "$ICON_BASE$it.png" }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api = "http://144.126.211.123/api"
    private val findApiService = RetrofitClient.build(app)ing)
data class CityItem(val id: Int, val nameAr: String, val regionId: Int)

data class ApiListing(
    val id: String,
    val title: String?,
    val price: Double?,
    val listingType: String?,
    val createdAt: String?,
    val images: List<String>,
    val sellerName: String?,
    val sellerAvatar: String?,
    val regionNameAr: String?,
    val city: String?
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api = "http://144.126.211.123/api"
    private val findApiService = RetrofitClient.build(app)

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _categories = MutableLiveData<List<ApiCategory>>()
    val categories: LiveData<List<ApiCategory>> get() = _categories

    /** Sub-categories shown in the home grid — updates when user taps a top tab on home */
    private val _homeSubCategories = MutableLiveData<List<ApiSubCategory>>(emptyList())
    val homeSubCategories: LiveData<List<ApiSubCategory>> get() = _homeSubCategories

    /** True while the home grid is populating (boot loading covers initial state) */
    private val _isHomeGridLoading = MutableLiveData<Boolean>(false)
    val isHomeGridLoading: LiveData<Boolean> get() = _isHomeGridLoading

    private val _regions = MutableLiveData<List<RegionItem>>()
    val regions: LiveData<List<RegionItem>> get() = _regions

    private val _allCities = MutableLiveData<List<CityItem>>()
    val allCities: LiveData<List<CityItem>> get() = _allCities

    private val _listings = MutableLiveData<List<ApiListing>>()
    // ── Search state ──────────────────────────────────────────────────────────

    private val _srchDraft = MutableStateFlow("")
    val srchDraft: StateFlow<String> get() = _srchDraft

    private val _srchTags = MutableLiveData<List<String>>(emptyList())
    val srchTags: LiveData<List<String>> get() = _srchTags

    private val _searchResults = MutableLiveData<List<ApiListing>>()
    val searchResults: LiveData<List<ApiListing>> get() = _searchResults

    private val _isSearchLoading = MutableLiveData<Boolean>(false)
    val isSearchLoading: LiveData<Boolean> get() = _isSearchLoading

    private val _isSearchEmpty = MutableLiveData<Boolean>(false)
    val isSearchEmpty: LiveData<Boolean> get() = _isSearchEmpty

    private val _searchErrorEvent = MutableLiveData<String?>()
    val searchErrorEvent: LiveData<String?> get() = _searchErrorEvent

    init {
        boot()
        viewModelScope.launch {
            _srchDraft
                .debounce(400L)
                .collect { draft ->
                    val tags = _srchTags.value ?: emptyList()
                    val active = tags.isNotEmpty() || draft.length >= 2
                    if (active) runSearch() else if (draft.isEmpty() && tags.isEmpty()) clearSearch()
                }
        }
    }ive: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        val srchDraftLiveData = _srchDraft.asLiveData()
        addSource(_srchTags) { tags ->
            val draft = _srchDraft.value
            value = tags.isNotEmpty() || draft.length >= 2
        }
        addSource(srchDraftLiveData) { draft ->
            val tags = _srchTags.value ?: emptyList()
            value = tags.isNotEmpty() || draft.length >= 2
        }
    }

    private var searchJob: Job? = null

    // ── Filter state ──────────────────────────────────────────────────────────

    var catIdx: Int = 0Loading = MutableLiveData<Boolean>(true)
    val isBootLoading: LiveData<Boolean> get() = _isBootLoading

    private val _isListingsLoading = MutableLiveData<Boolean>(false)
    val isListingsLoading: LiveData<Boolean> get() = _isListingsLoading

    /** True only when loading the first page (shows full shimmer) */
    private val _isFirstPageLoading = MutableLiveData<Boolean>(false)
    val isFirstPageLoading: LiveData<Boolean> get() = _isFirstPageLoading

    /** True when loading additional pages (shows footer spinner) */
    private val _isPagingLoading = MutableLiveData<Boolean>(false)
    val isPagingLoading: LiveData<Boolean> get() = _isPagingLoading

    private val _isEmptyState = MutableLiveData<Boolean>(false)
    val isEmptyState: LiveData<Boolean> get() = _isEmptyState

    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> get() = _errorEvent

    // ── Search state ──────────────────────────────────────────────────────────

    private val _srchDraft = MutableStateFlow("")
    val srchDraft: StateFlow<String> get() = _srchDraft

    private val _srchTags = MutableLiveData<List<String>>(emptyList())
    val srchTags: LiveData<List<String>> get() = _srchTags

    private val _searchResults = MutableLiveData<List<ApiListing>>()
    val searchResults: LiveData<List<ApiListing>> get() = _searchResults

    private val _isSearchLoading = MutableLiveData<Boolean>(false)
    val isSearchLoading: LiveData<Boolean> get() = _isSearchLoading

    private val _isSearchEmpty = MutableLiveData<Boolean>(false)
    val isSearchEmpty: LiveData<Boolean> get() = _isSearchEmpty

    private val _searchErrorEvent = MutableLiveData<String?>()
    init {
        boot()
        // Debounce pipeline: react to draft changes with 400ms delay
        viewModelScope.launch {
            _srchDraft
                .debounce(400L)
                .collect { draft ->
                    val tags = _srchTags.value ?: emptyList()
                    val active = tags.isNotEmpty() || draft.length >= 2
                    if (active) runSearch() else if (draft.isEmpty() && tags.isEmpty()) clearSearch()
                }
        }
    }Event: LiveData<String?> get() = _searchErrorEvent

    val isSearchActive: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        val srchDraftLiveData = _srchDraft.asLiveData()
        addSource(_srchTags) { tags ->
            val draft = _srchDraft.value
            value = tags.isNotEmpty() || draft.length >= 2
        }
        addSource(srchDraftLiveData) { draft ->
            val tags = _srchTags.value ?: emptyList()
            value = tags.isNotEmpty() || draft.length >= 2
        }
    }

    private var searchJob: Job? = null

    // ── Filter state ──────────────────────────────────────────────────────────

    var catIdx: Int = 0
        private set
    var catSubIdx: Int? = null
        private set
    var catExtraIdx: Int? = null
        private set
    var catType: String? = null
    var catRegId: Int? = null
    var catCityId: Int? = null = MutableLiveData<List<ApiListing>>()
    val searchResults: LiveData<List<ApiListing>> get() = _searchResults

MutableLiveData<Boolean>(false)
    val isSearchLoading: LiveData<Boolean> get() = _isSearchLoading

    private val _isSearchEmpty = MutableLiveData<Boolean>(false)
    val isSearchEmpty: LiveData<Boolean> get() = _isSearchEmpty

    private val _searchErrorEvent = MutableLiveData<String?>()
    val searchErrorEvent: LiveData<String?> get() = _searchErrorEvent

    val isSearchActive: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        val srchDraftLiveData = _srchDraft.asLiveData()
        addSource(_srchTags) { tags ->
            val draft = _srchDraft.value
            value = tags.isNotEmpty() || draft.length >= 2
        }
        addSource(srchDraftLiveData) { draft ->
            val tags = _srchTags.value ?: emptyList()
            value = tags.isNotEmpty() || draft.length >= 2
        }
    }

    private var searchJob: Job? = null

    // ── Filter state ──────────────────────────────────────────────────────────

    var catIdx: Int = 0
        private set
    var catSubIdx: Int? 
        private set
    var catExtraIdx: Int? = null
        private set
    var catType: String? = null
    var catRegId: Int? = null
    var catCityId: Int? = null

    // ── Pagination state ──────────────────────────────────────────────────────

    private var currentPage = 1
    private var lastPage = 1
    private var isFetching = false

    // ── Boot ──────────────────────────────────────────────────────────────────

    init {
        boot()
        // Debounce pipeline: react to draft changes with 400ms delay
        viewModelScope.launch {
            _srchDraft
                .debounce(400L)
                .collect { draft ->
                    val tags = _srchTags.value ?: emptyList()
                    val active = tags.isNotEmpty() || draft.length >= 2
                    if (active) runSearch() else if (draft.isEmpty() && tags.isEmpty()) clearSearch()
                }
        }
    }

    private fun boot() {
        _isBootLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = openGet("$api/search-filters")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val root = JSONObject(conn.inputStream.bufferedReader().readText())
                    val data = root.optJSONObject("data") ?: root
                    val cats = parseCategoriesFromFilters(data.optJSONArray("categories") ?: JSONArray())
                    val (regions, cities) = parseRegions(data.optJSONArray("regions") ?: JSONArray())
                    withContext(Dispatchers.Main) {
                        _categories.value = cats
                        _regions.value = regions
                        _allCities.value = cities
                        _isBootLoading.value = false
                    }
                    // Now fetch full category details (with sub-category icons) for all categories
                    fetchAllCategoryDetails(cats)
                } else {
                    withContext(Dispatchers.Main) { _isBootLoading.value = false }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBootLoading.value = false
                    _errorEvent.value = "تعذّر تحميل البيانات"
                }
            }
        }
    }

    /** Fetch /categories/{id} for every top-level category to get sub-category icons */
    private suspend fun fetchAllCategoryDetails(cats: List<ApiCategory>) {
        val enriched = cats.map { cat ->
            try {
                val conn = openGet("$api/categories/${cat.id}")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = JSONObject(conn.inputStream.bufferedReader().readText())
                    val data = body.optJSONObject("data") ?: body
                    val subArr = data.optJSONArray("sub_categories") ?: JSONArray()
                    val subs = mutableListOf<ApiSubCategory>()
                    for (i in 0 until subArr.length()) {
                        val s = subArr.getJSONObject(i)
                        if (s.optString("name_ar").trim() == "الكل") continue
                        val opts = mutableListOf<ApiFilterOption>()
                        val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                        for (k in 0 until optArr.length()) {
                            val fo = optArr.getJSONObject(k)
                            opts.add(ApiFilterOption(fo.getInt("id"), fo.getString("name_ar")))
                        }
                        sSubCategory(
                            id = s.getInt("id"),
                            nameAr = s.getString("name_ar"),
                            iconName = s.optString("icon").ifEmpty { null },
                            filterOptions = opts
                        ))
                    }
                    cat.copy(subCategories = subs)
                } else cat
            } catch (_: Exception) { cat }
        }
        withContext(Dispatchers.Main) {
            _categories.value = enriched
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    /** Fetch a single category's details and update _categories immediately */
    fun fetchCategoryDetails(categoryId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = openGet("$api/categories/$categoryId")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = JSONObj
                    val data = body.optJSONObject("data") ?: body
                    val subArr = data.optJSONArray("sub_categories") ?: JSONArray()
                    val subs = mutableListOf<ApiSubCategory>()
                    for (i in 0 until subArr.length()) {
                        val s = subArr.getJSONObject(i)
                        if (s.optString("name_ar").trim() == "الكل") continue
                        val opts = mutableListOf<ApiFilterOption>()
                        val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                        for (k in 0 until optArr.length()) {
                            val fo = optArr.getJSONObject(k)
                            opts.add(ApiFilterOption(fo.getInt("id"), fo.getString("name_ar")))
                        }
                        subs.add(ApiSubCategory(
                            id = s.getInt("id"),
                            nameAr = s.getString("name_ar"),
                            iconName = s.optString("icon { null },
                            filterOptions = opts
                        ))
                    }
                    withContext(Dispatchers.Main) {
                        val current = _categories.value ?: return@withContext
                        _categories.value = current.map { cat ->
                            if (cat.id == categoryId) cat.copy(subCategories = subs) else cat
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /** Sets catIdx only — no fetch, no state change. Used when showing sub-category grid. */
    fun setCategoryIndex(idx: Int) {
        catIdx = idx
        catSubIdx = null
        catExtraIdx = null
        catRegId = null
        catCityId = null
    }

    fun selectTopCategory(idx: Int, fetchImmediately: Boolean = false) {
        catIdx = idx
        catSubIdx = null
        catExtraIdx = null
        catRegId = null
        catCityId = null
        if (idx == 0) {
            _home = emptyList()
        } else if (fetchImmediately) {
            fetchListings(reset = true)
        }
    }

    /** Called when user taps a top-level category card in the home grid */
    // ── Search operations ─────────────────────────────────────────────────────

    fun onDraftChanged(text: String) {
        _srchDraft.value = text
    }

    fun commitDraftAsTag() {
        val draft = _srchDraft.value.trim()
        if (draft.isEmpty()) return
        val current = _srchTags.value ?: emptyList()
        if (current.any { it.equals(draft, ignoreCase = true) }) return
        _srchTags.value = current + draft
        _srchDraft.value = ""
        runSearch()
    }

    fun removeTag(tag: String) {
        val current = _srchTags.value ?: emptyList()
        val updated = current.filter { it != tag }
        _srchTags.value = updated
        val draft = _srchDraft.value
        if (updated.isEmpty() && draft.length < 2) {
            clearSearch()
        } else {
            runSearch()
        }
    }

    private fun buildQuery(): String {
        val tags = _srchTags.value ?: emptyList()
        val draft = _srchDraft.value.trim()
        val combined = (tags + listOf(draft)).filter { it.isNotEmpty() }.joinToString(" ")
        return URLEncoder.encode(combined, "UTF-8")
    }

    fun runSearch() {
        searchJob?.cancel()
        _isSearchLoading.value = true
        searchJob = viewModelScope.launch {
            try {
                val response = findApiService.searchListings(buildQuery())
                if (response.isSuccessful) {
                    val results = response.body()?.data?.map { it.toApiListing() } ?: emptyList()
                    _searchResults.value = results
                    _isSearchEmpty.value = results.isEmpty()
                } else {
                    _searchErrorEvent.value = "تعذّر تنفيذ البحث"
                }
            } catch (e: Exception) {
                _searchErrorEvent.value = "تعذّر تنفيذ البحث"
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _srchDraft.value = ""
        _srchTags.value = emptyList()
  
        _isHomeGridLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = openGet("$api/categories/${cat.id}")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = JSONObject(conn.inputStream.bufferedReader().readText())
                    val data = body.optJSONObject("data") ?: body
                    val subArr = data.optJSONArray("sub_categories") ?: JSONArray()
                    val subs = mutableListOf<ApiSubCategory>()
                    for (i in 0 until subArr.length()) {
                        val s = subArr.getJSONObject(i)
                        if (s.optString("name_ar").trim() == "الكل") continue
                        val opts = mutableListOf<AprOption>()
                        val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                        for (k in 0 until optArr.length()) {
                            val fo = optArr.getJSONObject(k)
                            opts.add(ApiFilterOption(fo.getInt("id"), fo.getString("name_ar")))
                        }
                        subs.add(ApiSubCategory(
                            id = s.getInt("id"),
                            nameAr = s.getString("name_ar"),
                            iconName = s.optString("icon").ifEmpty { null },
                            filterOptions = opts
                        ))
                    }
                    withContext(Dispatchers.Main) {
                        _homeSubCategories.value = subs
                        _isHomeGridLoading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // Fall back to whatever we already have from search-filters
                        _homeSubCategories.value = cat.subCategories
                        _isHomeGridLoading.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _homeSubCategories.value = cat.subCategories
                    _isHomeGridLoading.value = false
                }
            }
        }
    }

    fun selectSubCategory(subIdx: Int?) {
        catSubIdx = subIdx   // null = الكل (no sub filter)
        catExtraIdx = null
        catRegId = null
        catCityId = null
        fetchListings(reset = true)
    }

    fun selectExtra(extraIdx: Int?) {
        catExtraIdx = if (catExtraIdx == extraIdx) null else extraIdx
        fetchListings(reset = true)
    }

    fun selectType(type: String?) {
        catType = type
        if (catIdx > 0) fetchListings(reset = true)
    }

    fun selectRegion(regionId: Int?) {
    // ── Search operations ─────────────────────────────────────────────────────

    fun onDraftChanged(text: String) {
        _srchDraft.value = text
    }

    fun commitDraftAsTag() {
        val draft = _srchDraft.value.trim()
        if (draft.isEmpty()) return
        val current = _srchTags.value ?: emptyList()
        if (current.any { it.equals(draft, ignoreCase = true) }) return
        _srchTags.value = current + draft
        _srchDraft.value = ""
        runSearch()
    }

    fun removeTag(tag: String) {
        val current = _srchTags.value ?: emptyList()
        val updated = current.filter { it != tag }
        _srchTags.value = updated
        val draft = _srchDraft.value
        if (updated.isEmpty() && draft.length < 2) {
            clearSearch()
        } else {
            runSearch()
        }
    }

    private fun buildQuery(): String {
        val tags = _srchTags.value ?: emptyList()
        val draft = _srchDraft.value.trim()
        val combined = (tags + listOf(draft)).filter { it.isNotEmpty() }.joinToString(" ")
        return URLEncoder.encode(combined, "UTF-8")
    }

    fun runSearch() {
        searchJob?.cancel()
        _isSearchLoading.value = true
        searchJob = viewModelScope.launch {
            try {
                val response = findApiService.searchListings(buildQuery())
                if (response.isSuccessful) {
                    val results = response.body()?.data?.map { it.toApiListing() } ?: emptyList()
                    _searchResults.value = results
                    _isSearchEmpty.value = results.isEmpty()
                } else {
                    _searchErrorEvent.value = "تعذّر تنفيذ البحث"
                }
            } catch (e: Exception) {
                _searchErrorEvent.value = "تعذّر تنفيذ البحث"
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _srchDraft.value = ""
        _srchTags.value = emptyList()
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
        _isSearchEmpty.value = false
        _searchErrorEvent.value = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
        catCityId = null
        if (catIdx > 0) fetchListings(reset = true)
    }

    fun selectCity(cityId: Int?) {
        catCityId = cityId
        if (catIdx > 0) fetchListings(reset = true)
    }

    /** Pull-to-refresh: reset and reload first page */
    fun refresh() {
        if (catIdx > 0) fetchListings(reset = true)
    }

    /** Called by scroll listener when user reaches the bottom */
    fun loadNextPage() {
        if (!isFetching && currentPage < lastPage) {
            fetchListings(reset = false)
        }
    }

    fun hasMorePages() = currentPage < lastPage

    // ── Fetch listings ────────────────────────────────────────────────────────

    fun fetchListings(reset: Boolean = true) {
        val cats = _categories.value ?: return
        if (catIdx == 0 || catIdx > cats.size) return
        if (isFetching) return

        val cat = cats[catIdx - 1]
        val subs = cat.subCategories
        val ss = catSubIdx?.let { subs.getOrNull(it) }
        val extras = ss?.filterOptions ?: emptyList()
        val se = catEl(it) }

        if (reset) {
            currentPage = 1
            _listings.value = emptyList()
            _isFirstPageLoading.value = true
            _isEmptyState.value = false
        } else {
            currentPage++
            _isPagingLoading.value = true
        }

        val page = currentPage
        val params = StringBuilder("page=$page&per_page=$PAGE_SIZE&category_id=${cat.id}")
        ss?.let { params.append("&sub_category_id=${it.id}") }
        se?.let { params.append("&filter_option_id=${it.id}") }
        catRegId?.let { params.append("&region_id=$it") }
        catCityId?.let { cId ->
            val cityName = _allCities.value?.find { it.id == cId }?.nameAr
            cityName?.let { params.append("&city=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        }
        catType?.let { params.append("&listing_type=$it") }

        isFetching = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = openGet("$api/listings?$params")
(conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = JSONObject(conn.inputStream.bufferedReader().readText())
                    val arr = body.optJSONArray("data") ?: JSONArray()
                    val meta = body.optJSONObject("meta")
                    val fetchedLastPage = meta?.optInt("last_page", 1) ?: 1
                    val result = parseListings(arr)
                    withContext(Dispatchers.Main) {
                        lastPage = fetchedLastPage
                        val current = if (reset) emptyList() else (_listings.value ?: emptyList())
                        _listings.value = current + result
                        _isFirstPageLoading.value = false
                        _isPagingLoading.value = false
                        _isEmptyState.value = reset && result.isEmpty()
                        isFetching = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isFirstPageLoadlue = false
                        _isPagingLoading.value = false
                        _isEmptyState.value = reset
                        isFetching = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isFirstPageLoading.value = false
                    _isPagingLoading.value = false
                    _errorEvent.value = "تعذر تحميل الإعلانات"
                    isFetching = false
                }
            }
        }
    }

    // ── Search operations ─────────────────────────────────────────────────────

    fun onDraftChanged(text: String) {
        _srchDraft.value = text
    }

    fun commitDraftAsTag() {
        val draft = _srchDraft.value.trim()
        if (draft.isEmpty()) return
        val current = _srchTags.value ?: emptyList()
        if (current.any { it.equals(draft, ignoreCase = true) }) return
        _srchTags.value = current + draft
        _srchDraft.value = ""
     
    }

    fun removeTag(tag: String) {
        val current = _srchTags.value ?: emptyList()
        val updated = current.filter { it != tag }
        _srchTags.value = updated
        val draft = _srchDraft.value
        if (updated.isEmpty() && draft.length < 2) {
            clearSearch()
        } else {
            runSearch()
        }
    }

    private fun buildQuery(): String {
        val tags = _srchTags.value ?: emptyList()
        val draft = _srchDraft.value.trim()
        val combined = (tags + listOf(draft)).filter { it.isNotEmpty() }.joinToString(" ")
        return URLEncoder.encode(combined, "UTF-8")
    }

    fun runSearch() {
        searchJob?.cancel()
        _isSearchLoading.value = true
        searchJob = viewModelScope.launch {
            try {
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
JSONArray("images")
            if (imgArr != null) for (j in 0 until imgArr.length()) images.add(imgArr.getString(j))
            val seller = o.optJSONObject("seller")
            val region = o.optJSONObject("region")
            list.add(ApiListing(
                id = o.optString("id"),
                title = o.optString("title").ifEmpty { null },
                price = if (!o.isNull("price")) o.optDouble("price") else null,
                listingType = o.optString("listing_type").ifEmpty { null },
           for (j in 0 until cArr.length()) {
                val c = cArr.getJSONObject(j)
                cities.add(CityItem(c.getInt("id"), c.getString("name_ar"), rId))
            }
        }
        return Pair(regions, cities)
    }

    private fun parseListings(arr: JSONArray): List<ApiListing> {
        val list = mutableListOf<ApiListing>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val images = mutableListOf<String>()
            val imgArr = o.opt   subCategories = subs
            ))
        }
        return list
    }

    private fun parseRegions(arr: JSONArray): Pair<List<RegionItem>, List<CityItem>> {
        val regions = mutableListOf<RegionItem>()
        val cities = mutableListOf<CityItem>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val rId = r.getInt("id")
            regions.add(RegionItem(rId, r.getString("name_ar")))
            val cArr = r.optJSONArray("cities") ?: JSONArray()
    ring("name_ar")))
                }
                subs.add(ApiSubCategory(
                    id = s.getInt("id"),
                    nameAr = s.getString("name_ar"),
                    iconName = s.optString("icon").ifEmpty { null },
                    filterOptions = opts
                ))
            }
            list.add(ApiCategory(
                id = o.getInt("id"),
                nameAr = o.getString("name_ar"),
                iconName = o.optString("icon").ifEmpty { null },
             y("sub_categories") ?: JSONArray()
            for (j in 0 until subArr.length()) {
                val s = subArr.getJSONObject(j)
                if (s.optString("name_ar").trim() == "الكل") continue
                val opts = mutableListOf<ApiFilterOption>()
                val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                for (k in 0 until optArr.length()) {
                    val fo = optArr.getJSONObject(k)
                    opts.add(ApiFilterOption(fo.getInt("id"), fo.getSttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn
    }

    private fun parseCategoriesFromFilters(arr: JSONArray): List<ApiCategory> {
        val list = mutableListOf<ApiCategory>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val subs = mutableListOf<ApiSubCategory>()
            val subArr = o.optJSONArrasrchTags.value = emptyList()
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
        _isSearchEmpty.value = false
        _searchErrorEvent.value = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun citiesForRegion(regionId: Int?): List<CityItem> {
        if (regionId == null) return emptyList()
        return _allCities.value?.filter { it.regionId == regionId } ?: emptyList()
    }

    private fun openGet(url: String): HtarchResults.value = results
                    _isSearchEmpty.value = results.isEmpty()
                } else {
                    _searchErrorEvent.value = "تعذّر تنفيذ البحث"
                }
            } catch (e: Exception) {
                _searchErrorEvent.value = "تعذّر تنفيذ البحث"
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _srchDraft.value = ""
        _                val response = findApiService.searchListings(buildQuery())
                if (response.isSuccessful) {
                    val results = response.body()?.data?.map { it.toApiListing() } ?: emptyList()
                    _se