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

// ── Data models ──────────────────────────────────────────────────────────────

data class ApiCategory(
    val id: Int,
    val nameAr: String,
    val iconUrl: String? = null,
    val subCategories: List<ApiSubCategory> = emptyList()
)

data class ApiSubCategory(
    val id: Int,
    val nameAr: String,
    val iconUrl: String? = null,
    val filterOptions: List<ApiFilterOption> = emptyList()
)

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

    private val _isEmptyState = MutableLiveData<Boolean>(false)
    val isEmptyState: LiveData<Boolean> get() = _isEmptyState

    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> get() = _errorEvent

    // ── Filter state (mirrors S.cat* in HTML) ─────────────────────────────────

    /** 0 = home/all, 1..N = index into categories list */
    var catIdx: Int = 0
        private set

    /** index into current category's sub_categories, null = "الكل" */
    var catSubIdx: Int? = null
        private set

    /** index into selected sub's filter_options, null = "الكل" */
    var catExtraIdx: Int? = null
        private set

    var catType: String? = null   // null=all, "offer", "request"
    var catRegId: Int? = null
    var catCityId: Int? = null

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

    // ── Selection logic ───────────────────────────────────────────────────────

    /** Called when user taps a top-level category tab. Resets sub/extra/region/city. */
    fun selectTopCategory(idx: Int) {
        catIdx = idx
        catSubIdx = null
        catExtraIdx = null
        catRegId = null
        catCityId = null
        if (idx > 0) fetchListings()
    }

    /** Called when user taps a sub-category chip. Resets extra/region/city. */
    fun selectSubCategory(subIdx: Int?) {
        catSubIdx = if (catSubIdx == subIdx) null else subIdx   // toggle
        catExtraIdx = null
        catRegId = null
        catCityId = null
        fetchListings()
    }

    /** Called when user taps a filter-option (extra) chip. */
    fun selectExtra(extraIdx: Int?) {
        catExtraIdx = if (catExtraIdx == extraIdx) null else extraIdx
        fetchListings()
    }

    /** Called when type chip changes. */
    fun selectType(type: String?) {
        catType = type
        if (catIdx > 0) fetchListings()
    }

    /** Called when region spinner changes. */
    fun selectRegion(regionId: Int?) {
        catRegId = regionId
        catCityId = null
        if (catIdx > 0) fetchListings()
    }

    /** Called when city spinner changes. */
    fun selectCity(cityId: Int?) {
        catCityId = cityId
        if (catIdx > 0) fetchListings()
    }

    // ── Fetch listings ────────────────────────────────────────────────────────

    fun fetchListings() {
        val cats = _categories.value ?: return
        if (catIdx == 0 || catIdx > cats.size) return
        val cat = cats[catIdx - 1]

        val subs = cat.subCategories
        val ss = if (catSubIdx != null && catSubIdx!! < subs.size) subs[catSubIdx!!] else null
        val extras = ss?.filterOptions ?: emptyList()
        val se = if (catExtraIdx != null && catExtraIdx!! < extras.size) extras[catExtraIdx!!] else null

        val params = StringBuilder("page=1&category_id=${cat.id}")
        ss?.let { params.append("&sub_category_id=${it.id}") }
        se?.let { params.append("&filter_option_id=${it.id}") }
        catRegId?.let { params.append("&region_id=$it") }
        catCityId?.let { cId ->
            val cityName = _allCities.value?.find { it.id == cId }?.nameAr
            cityName?.let { params.append("&city=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        }
        catType?.let { params.append("&listing_type=$it") }

        _isListingsLoading.value = true
        _isEmptyState.value = false
        _listings.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = openGet("$api/listings?$params")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val arr = JSONObject(conn.inputStream.bufferedReader().readText())
                        .optJSONArray("data") ?: JSONArray()
                    val result = parseListings(arr)
                    withContext(Dispatchers.Main) {
                        _listings.value = result
                        _isListingsLoading.value = false
                        _isEmptyState.value = result.isEmpty()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isListingsLoading.value = false
                        _isEmptyState.value = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isListingsLoading.value = false
                    _errorEvent.value = "تعذر تحميل الإعلانات"
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
                // Skip any "الكل" items the API might return — we add it manually in the UI
                if (s.optString("name_ar").trim() == "الكل") continue
                val opts = mutableListOf<ApiFilterOption>()
                val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                for (k in 0 until optArr.length()) {
                    val fo = optArr.getJSONObject(k)
                    opts.add(ApiFilterOption(fo.getInt("id"), fo.getString("name_ar")))
                }
                subs.add(ApiSubCategory(s.getInt("id"), s.getString("name_ar"),
                    s.optString("icon").ifEmpty { null }, opts))
            }
            list.add(ApiCategory(o.getInt("id"), o.getString("name_ar"),
                o.optString("icon").ifEmpty { null }, subs))
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
