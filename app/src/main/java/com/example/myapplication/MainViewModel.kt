package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.MockFindRepository
import com.example.myapplication.models.Category
import com.example.myapplication.models.FilterOption
import com.example.myapplication.models.Listing
import com.example.myapplication.models.ListingRegion
import com.example.myapplication.models.ListingSeller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RegionItem(val id: Int, val nameAr: String)
data class CityItem(val id: Int, val nameAr: String, val regionId: Int)

class MainViewModel : ViewModel() {

    private val repository = MockFindRepository()
    private val api = "http://144.126.211.123/api"

    // ── LiveData ──────────────────────────────────────────────────────────────
    private val _activeFilterLevels = MutableLiveData<List<List<Category>>>()
    val activeFilterLevels: LiveData<List<List<Category>>> get() = _activeFilterLevels

    private val _selectionPath = MutableLiveData<List<Category>>()
    val selectionPath: LiveData<List<Category>> get() = _selectionPath

    private val _mainContentItems = MutableLiveData<List<Any>>()
    val mainContentItems: LiveData<List<Any>> get() = _mainContentItems

    private val _isEmptyState = MutableLiveData<Boolean>()
    val isEmptyState: LiveData<Boolean> get() = _isEmptyState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _isSubLoading = MutableLiveData<Boolean>()
    val isSubLoading: LiveData<Boolean> get() = _isSubLoading

    private val _apiErrorEvent = MutableLiveData<String>()
    val apiErrorEvent: LiveData<String> get() = _apiErrorEvent

    private val _listings = MutableLiveData<List<Listing>>()
    val listings: LiveData<List<Listing>> get() = _listings

    private val _listingsLoading = MutableLiveData<Boolean>()
    val listingsLoading: LiveData<Boolean> get() = _listingsLoading

    private val _regions = MutableLiveData<List<RegionItem>>()
    val regions: LiveData<List<RegionItem>> get() = _regions

    private val _cities = MutableLiveData<List<CityItem>>()
    val cities: LiveData<List<CityItem>> get() = _cities

    // ── Filter state (mirrors S.cat* in HTML) ─────────────────────────────────
    private var rootCategories: List<Category> = emptyList()
    val selectionStack = mutableListOf<Category>() // exposed for MainActivity depth tracking

    var catIdx: Int = 0           // current top tab index
    var catSubIdx: Int? = null    // selected sub-category index
    var catExtraIdx: Int? = null  // selected filter_option index
    var catType: String? = null   // null=all, "offer", "request"
    var catRegId: Int? = null     // selected region id
    var catCityId: Int? = null    // selected city id

    init { loadData() }

    // ── Bootstrap: load categories + search-filters ───────────────────────────

    private fun loadData() {
        _isLoading.postValue(true)
        viewModelScope.launch(Dispatchers.IO) {
            // Load categories
            try {
                val conn = URL("$api/categories").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("data")
                    val cats = mutableListOf<Category>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        cats.add(Category(id = o.getInt("id").toString(), name = o.getString("name_ar"), isHome = (i == 0)))
                    }
                    withContext(Dispatchers.Main) {
                        rootCategories = cats
                        _isLoading.value = false
                        cats.firstOrNull()?.let { selectItemAtLevel(it, 0) }
                    }
                } else loadMockData()
            } catch (_: Exception) { loadMockData() }

            // Load regions + cities from search-filters
            try {
                val conn = URL("$api/search-filters").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val data = JSONObject(conn.inputStream.bufferedReader().readText()).let {
                        it.optJSONObject("data") ?: it
                    }
                    val regArr = data.optJSONArray("regions") ?: JSONArray()
                    val regionList = mutableListOf<RegionItem>()
                    val cityList = mutableListOf<CityItem>()
                    for (i in 0 until regArr.length()) {
                        val r = regArr.getJSONObject(i)
                        val rId = r.getInt("id")
                        regionList.add(RegionItem(rId, r.getString("name_ar")))
                        val citiesArr = r.optJSONArray("cities") ?: JSONArray()
                        for (j in 0 until citiesArr.length()) {
                            val c = citiesArr.getJSONObject(j)
                            cityList.add(CityItem(c.getInt("id"), c.getString("name_ar"), rId))
                        }
                    }
                    withContext(Dispatchers.Main) {
                        _regions.value = regionList
                        _cities.value = cityList
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadMockData() {
        withContext(Dispatchers.Main) {
            val root = repository.getCategories()
            rootCategories = root
            _isLoading.value = false
            root.firstOrNull()?.let { selectItemAtLevel(it, 0) }
        }
    }

    // ── Category selection ────────────────────────────────────────────────────

    fun selectItemAtLevel(item: Category, level: Int) {
        while (selectionStack.size > level) selectionStack.removeAt(selectionStack.size - 1)
        selectionStack.add(item)
        _selectionPath.value = selectionStack.toList()

        if (!item.isHome && item.name != "الكل" && item.subItems.isEmpty() && !item.isFilterOption) {
            fetchSubCategories(item)
        } else {
            publishFilterLevels()
            updateContent(item)
        }
    }

    private fun fetchSubCategories(item: Category) {
        _isSubLoading.postValue(true)
        publishFilterLevels()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = URL("$api/categories/${item.id}").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val subArr = JSONObject(conn.inputStream.bufferedReader().readText())
                        .optJSONObject("data")?.optJSONArray("sub_categories")
                    val children = mutableListOf<Category>()
                    if (subArr != null) {
                        for (i in 0 until subArr.length()) {
                            val o = subArr.getJSONObject(i)
                            val childId = o.getInt("id").toString()
                            val filterOpts = mutableListOf<FilterOption>()
                            val fa = o.optJSONArray("filter_options")
                            if (fa != null) for (f in 0 until fa.length()) {
                                val fo = fa.getJSONObject(f)
                                filterOpts.add(FilterOption(fo.getInt("id").toString(), fo.getString("name_ar")))
                            }
                            val subItems = if (filterOpts.isNotEmpty())
                                listOf(Category("${childId}_all", "الكل", isFilterOption = true)) +
                                filterOpts.map { Category(it.id, it.name, isFilterOption = true) }
                            else emptyList()
                            children.add(Category(
                                id = childId, name = o.getString("name_ar"),
                                iconUrl = o.optString("icon").ifEmpty { null },
                                filterOptions = filterOpts, subItems = subItems
                            ))
                        }
                    }
                    withContext(Dispatchers.Main) {
                        item.subItems = children
                        _isSubLoading.value = false
                        publishFilterLevels()
                        updateContent(item)
                    }
                } else withContext(Dispatchers.Main) { _isSubLoading.value = false; publishFilterLevels(); updateContent(item) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { _isSubLoading.value = false; _apiErrorEvent.value = "حدث خطأ"; publishFilterLevels(); updateContent(item) }
            }
        }
    }

    private fun publishFilterLevels() {
        val levels = mutableListOf<List<Category>>()
        levels.add(rootCategories)
        for (item in selectionStack) {
            if (item.name == "الكل") break
            if (item.subItems.isNotEmpty()) levels.add(item.subItems)
        }
        _activeFilterLevels.value = levels
    }

    private fun updateContent(item: Category) {
        if (item.isHome) {
            // Home: show root category grid
            _mainContentItems.value = rootCategories.filter { !it.isHome }
            _listings.value = emptyList()
            _isEmptyState.value = false
        } else if (item.subItems.isNotEmpty() && !item.isFilterOption) {
            // Has sub-categories → show them as a grid (drill down)
            _mainContentItems.value = item.subItems.filter { it.name != "الكل" }
            _listings.value = emptyList()
            _isEmptyState.value = false
        } else {
            // Leaf node → fetch listings with all accumulated filters
            catIdx = rootCategories.indexOfFirst { it.id == item.id }.let {
                if (it >= 0) it + 1 else catIdx
            }
            fetchListings()
        }
    }

    // ── Fetch listings — mirrors fetchCatList() in HTML ───────────────────────

    fun fetchListings() {
        _listingsLoading.postValue(true)
        _isEmptyState.postValue(false)
        _listings.postValue(emptyList()) // clear previous results immediately

        // Build params exactly like the HTML does
        // Walk the selection stack to find category, sub-category, filter_option
        val stackCats = selectionStack.filter { !it.isHome && !it.isFilterOption }
        val cat = stackCats.getOrNull(0) ?: return
        val sub = stackCats.getOrNull(1)
        val filterOpt = selectionStack.lastOrNull { it.isFilterOption }

        val params = StringBuilder("page=1&category_id=${cat.id}")
        sub?.let { params.append("&sub_category_id=${it.id}") }
        filterOpt?.let { params.append("&filter_option_id=${it.id}") }
        catRegId?.let { params.append("&region_id=$it") }
        catCityId?.let { cId ->
            val cityName = _cities.value?.find { it.id == cId }?.nameAr
            cityName?.let { params.append("&city=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        }
        catType?.let { params.append("&listing_type=$it") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = URL("$api/listings?$params").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 8000; conn.readTimeout = 8000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val arr = JSONObject(conn.inputStream.bufferedReader().readText())
                        .optJSONArray("data") ?: JSONArray()
                    val result = mutableListOf<Listing>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val images = mutableListOf<String>()
                        val imgArr = o.optJSONArray("images")
                        if (imgArr != null) for (j in 0 until imgArr.length()) images.add(imgArr.getString(j))
                        val seller = o.optJSONObject("seller")?.let { s ->
                            ListingSeller(s.optInt("id"), s.optString("name").ifEmpty { null }, s.optString("avatar").ifEmpty { null })
                        }
                        val region = o.optJSONObject("region")?.let { r ->
                            ListingRegion(r.optInt("id"), r.optString("name_ar").ifEmpty { null })
                        }
                        result.add(Listing(
                            id = o.optString("id"),
                            title = o.optString("title").ifEmpty { null },
                            price = if (!o.isNull("price")) o.optDouble("price") else null,
                            description = o.optString("description").ifEmpty { null },
                            listingType = o.optString("listing_type").ifEmpty { null },
                            status = o.optString("status").ifEmpty { null },
                            createdAt = o.optString("created_at").ifEmpty { null },
                            images = images, seller = seller, region = region,
                            city = o.optString("city").ifEmpty { null },
                            categoryId = if (!o.isNull("category_id")) o.optInt("category_id") else null,
                            subCategoryId = if (!o.isNull("sub_category_id")) o.optInt("sub_category_id") else null,
                            regionId = if (!o.isNull("region_id")) o.optInt("region_id") else null
                        ))
                    }
                    withContext(Dispatchers.Main) {
                        _listings.value = result
                        _listingsLoading.value = false
                        _isEmptyState.value = result.isEmpty()
                        _mainContentItems.value = emptyList()
                    }
                } else withContext(Dispatchers.Main) { _listingsLoading.value = false; _isEmptyState.value = true }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { _listingsLoading.value = false; _apiErrorEvent.value = "تعذر تحميل الإعلانات" }
            }
        }
    }
}
