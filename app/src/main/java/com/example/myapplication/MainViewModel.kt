package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val ICON_BASE = "https://ocebfvgwgpebjxetnixc.supabase.co/storage/v1/object/public/listings-images/subCatigory/"
private const val PAGE_SIZE = 20

// ── Data models ───────────────────────────────────────────────────────────────

data class ApiCategory(
    val id: Int,
    val nameAr: String,
    val nameEn: String? = null,
    val iconName: String? = null,
    val subCategories: List<ApiSubCategory> = emptyList()
) {
    val iconUrl: String? get() = iconName?.let { name ->
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@let null
        if (cleanName.startsWith("http")) return@let cleanName
        val finalName = if (cleanName.lowercase().endsWith(".png")) cleanName else "$cleanName.png"
        "$ICON_BASE$finalName"
    }
}

data class ApiSubCategory(
    val id: Int,
    val nameAr: String,
    val nameEn: String? = null,
    val iconName: String? = null,
    val filterOptions: List<ApiFilterOption> = emptyList()
) {
    val iconUrl: String? get() = iconName?.let { name ->
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@let null
        if (cleanName.startsWith("http")) return@let cleanName
        val finalName = if (cleanName.lowercase().endsWith(".png")) cleanName else "$cleanName.png"
        "$ICON_BASE$finalName"
    }
}

data class ApiFilterOption(val id: Int, val nameAr: String, val nameEn: String? = null)

data class RegionItem(val id: Int, val nameAr: String, val nameEn: String? = null)
data class CityItem(val id: Int, val nameAr: String, val nameEn: String? = null, val regionId: Int)

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

    private val apiPublic = RetrofitClient.apiService

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _categories = MutableLiveData<List<ApiCategory>>()
    val categories: LiveData<List<ApiCategory>> get() = _categories

    private val _homeSubCategories = MutableLiveData<List<ApiSubCategory>>(emptyList())
    val homeSubCategories: LiveData<List<ApiSubCategory>> get() = _homeSubCategories

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

    private val _isFirstPageLoading = MutableLiveData<Boolean>(false)
    val isFirstPageLoading: LiveData<Boolean> get() = _isFirstPageLoading

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
    init {
        boot()
    }

    fun setError(msg: String?) {
        _errorEvent.value = msg
    }

    private fun boot() {
        _isBootLoading.value = true
        android.util.Log.d("MainVM", "Booting via Retrofit")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch Categories
                val catRes = apiPublic.getCategories()
                if (catRes.isSuccessful) {
                    val bodyText = catRes.body()?.string() ?: ""
                    android.util.Log.d("MainVM", "Categories RAW: $bodyText")
                    
                    val dataArr = try {
                        val root = JSONObject(bodyText)
                        root.optJSONArray("data") ?: JSONArray().apply { put(root) } 
                    } catch (e: Exception) {
                        try { JSONArray(bodyText) } catch (e2: Exception) { JSONArray() }
                    }
                    
                    val parsed = parseMainCategories(dataArr)
                    // Robust filter for duplicate Home
                    val filtered = parsed.filter { 
                        it.id != 1 && it.id != 0 && 
                        it.nameAr.trim() != "الرئيسية" && 
                        it.nameAr.trim() != "الرئيسيه" 
                    }
                    
                    withContext(Dispatchers.Main) {
                        _categories.value = filtered
                        _isBootLoading.value = false
                    }
                    
                    if (filtered.isNotEmpty()) {
                        fetchAllCategoryDetails(filtered)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isBootLoading.value = false
                        _errorEvent.value = "فشل تحميل الأقسام: ${catRes.code()}"
                    }
                }

                // 2. Fetch Regions
                launch(Dispatchers.IO) {
                    try {
                        val res = apiPublic.getSearchFilters()
                        if (res.isSuccessful) {
                            val body = JSONObject(res.body()?.string() ?: "")
                            val data = body.optJSONObject("data") ?: body
                            val tuple = parseRegions(data.optJSONArray("regions") ?: JSONArray())
                            withContext(Dispatchers.Main) {
                                _regions.value = tuple.first
                                _allCities.value = tuple.second
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Boot error", e)
                withContext(Dispatchers.Main) {
                    _isBootLoading.value = false
                    _errorEvent.value = "خطأ: ${e.localizedMessage}"
                }
            }
        }
    }

    private suspend fun fetchAllCategoryDetails(cats: List<ApiCategory>) {
        cats.map { cat ->
            viewModelScope.async(Dispatchers.IO) {
                try {
                    val res = apiPublic.getCategoryDetails(cat.id)
                    if (res.isSuccessful) {
                        val body = JSONObject(res.body()?.string() ?: "")
                        val data = body.optJSONObject("data") ?: body
                        val subArr = data.optJSONArray("sub_categories") ?: JSONArray()
                        val subs = mutableListOf<ApiSubCategory>()
                        
                        for (i in 0 until subArr.length()) {
                            val s = subArr.getJSONObject(i)
                            
                            val opts = mutableListOf<ApiFilterOption>()
                            val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                            for (k in 0 until optArr.length()) {
                                val fo = optArr.getJSONObject(k)
                                opts.add(ApiFilterOption(fo.getInt("id"), fo.optString("name_ar", ""), fo.optString("name_en", "").ifEmpty { null }))
                            }
                            
                            subs.add(ApiSubCategory(
                                id = s.getInt("id"),
                                nameAr = s.optString("name_ar", ""),
                                nameEn = s.optString("name_en", "").ifEmpty { null },
                                iconName = if (s.isNull("icon")) null else s.optString("icon").ifEmpty { null },
                                filterOptions = opts
                            ))
                        }
                        
                        val updated = cat.copy(subCategories = subs)
                        withContext(Dispatchers.Main) {
                            val current = _categories.value ?: emptyList()
                            _categories.value = current.map { if (it.id == updated.id) updated else it }
                        }
                    }
                } catch (_: Exception) {}
            }
        }.awaitAll()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectTopCategory(idx: Int) {
        catIdx = idx
        catSubIdx = null
        catExtraIdx = null
        catRegId = null
        catCityId = null
    }

    fun selectSubCategory(subIdx: Int?) {
        catSubIdx = subIdx
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

    fun hasMorePages() = currentPage < lastPage

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

        isFetching = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cityId = catCityId
                val cityName = if (cityId != null) {
                    _allCities.value?.find { it.id == cityId }?.nameAr
                } else null

                val res = apiPublic.getListingsCombined(
                    page = currentPage,
                    perPage = PAGE_SIZE,
                    categoryId = cat.id,
                    subCategoryId = ss?.id,
                    filterOptionId = se?.id,
                    regionId = catRegId,
                    city = cityName,
                    listingType = catType
                )

                if (res.isSuccessful) {
                    val root = JSONObject(res.body()?.string() ?: "")
                    val arr = root.optJSONArray("data") ?: JSONArray()
                    val meta = root.optJSONObject("meta")
                    val fetchedLast = meta?.optInt("last_page", 1) ?: 1
                    val result = parseListings(arr)
                    
                    withContext(Dispatchers.Main) {
                        lastPage = fetchedLast
                        val current = if (reset) emptyList() else (_listings.value ?: emptyList())
                        _listings.value = current + result
                        _isFirstPageLoading.value = false
                        _isPagingLoading.value = false
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isFirstPageLoading.value = false
                    _isPagingLoading.value = false
                }
            } finally {
                isFetching = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun citiesForRegion(regionId: Int?): List<CityItem> {
        if (regionId == null) return emptyList()
        return _allCities.value?.filter { it.regionId == regionId } ?: emptyList()
    }

    private fun parseMainCategories(arr: JSONArray): List<ApiCategory> {
        val list = mutableListOf<ApiCategory>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(ApiCategory(
                id = o.getInt("id"),
                nameAr = o.optString("name_ar", ""),
                nameEn = o.optString("name_en", "").ifEmpty { null },
                iconName = if (o.isNull("icon")) null else o.optString("icon").ifEmpty { null }
            ))
        }
        return list
    }

    private fun parseRegions(arr: JSONArray): Pair<List<RegionItem>, List<CityItem>> {
        val regs = mutableListOf<RegionItem>()
        val cities = mutableListOf<CityItem>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val rId = r.getInt("id")
            regs.add(RegionItem(rId, r.optString("name_ar", ""), r.optString("name_en", "").ifEmpty { null }))
            val cArr = r.optJSONArray("cities") ?: JSONArray()
            for (j in 0 until cArr.length()) {
                val c = cArr.getJSONObject(j)
                cities.add(CityItem(c.getInt("id"), c.optString("name_ar", ""), c.optString("name_en", "").ifEmpty { null }, rId))
            }
        }
        return regs to cities
    }

    private fun parseListings(arr: JSONArray): List<ApiListing> {
        val list = mutableListOf<ApiListing>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val imgArr = o.optJSONArray("images") ?: JSONArray()
            val imgs = mutableListOf<String>()
            for (j in 0 until imgArr.length()) imgs.add(imgArr.optString(j))
            
            val seller = o.optJSONObject("seller")
            val reg = o.optJSONObject("region")
            
            list.add(ApiListing(
                id = o.optString("id", ""),
                title = o.optString("title", ""),
                price = o.optDouble("price", 0.0),
                listingType = o.optString("listing_type", ""),
                createdAt = o.optString("created_at", ""),
                images = imgs,
                sellerName = seller?.optString("name", ""),
                sellerAvatar = seller?.optString("avatar", ""),
                regionNameAr = reg?.optString("name_ar", ""),
                city = o.optString("city", "")
            ))
        }
        return list
    }
}