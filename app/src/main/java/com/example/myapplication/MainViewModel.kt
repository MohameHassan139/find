package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

data class ApiFilterOption(val id: Int, val nameAr: String)

data class RegionItem(val id: Int, val nameAr: String)
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

class MainViewModel : ViewModel() {

    private val api = "http://144.126.211.123/api"

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
    val listings: LiveData<List<ApiListing>> get() = _listings

    private val _isBootLoading = MutableLiveData<Boolean>(true)
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

    // ── Filter state ──────────────────────────────────────────────────────────

    var catIdx: Int = 0
        private set
    var catSubIdx: Int? = null
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

    init { boot() }

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
                        subs.add(ApiSubCategory(
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
                        subs.add(ApiSubCategory(
                            id = s.getInt("id"),
                            nameAr = s.getString("name_ar"),
                            iconName = s.optString("icon").ifEmpty { null },
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
            _homeSubCategories.value = emptyList()
        } else if (fetchImmediately) {
            fetchListings(reset = true)
        }
    }

    /** Called when user taps a top-level category card in the home grid */
    fun selectHomeCategoryForGrid(cat: ApiCategory) {
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
                        val opts = mutableListOf<ApiFilterOption>()
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
        catRegId = regionId
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
        val se = catExtraIdx?.let { extras.getOrNull(it) }

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
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
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
                        _isFirstPageLoading.value = false
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun citiesForRegion(regionId: Int?): List<CityItem> {
        if (regionId == null) return emptyList()
        return _allCities.value?.filter { it.regionId == regionId } ?: emptyList()
    }

    private fun openGet(url: String): HttpURLConnection {
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
            val subArr = o.optJSONArray("sub_categories") ?: JSONArray()
            for (j in 0 until subArr.length()) {
                val s = subArr.getJSONObject(j)
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
                    iconName = s.optString("icon").ifEmpty { null },
                    filterOptions = opts
                ))
            }
            list.add(ApiCategory(
                id = o.getInt("id"),
                nameAr = o.getString("name_ar"),
                iconName = o.optString("icon").ifEmpty { null },
                subCategories = subs
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
