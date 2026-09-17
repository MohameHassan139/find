package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

import android.content.Context
import android.content.res.Configuration

object MediaUrlHelper {
    const val MEDIA_BASE = "https://ocebfvgwgpebjxetnixc.supabase.co/storage/v1/object/public/listings-images/Finds-media/"

    fun getIconUrl(iconName: String?, isDark: Boolean = false): String? {
        if (iconName.isNullOrBlank()) return null
        val clean = iconName.trim()
        val modeFolder = if (isDark) "dark" else "light"

        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            if (clean.contains("/subCatigory/")) {
                val fileName = clean.substringAfterLast("/")
                val nameWithoutExt = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
                return "$MEDIA_BASE$modeFolder/$nameWithoutExt.svg"
            }
            if (clean.contains("/Finds-media/")) {
                val fileName = clean.substringAfterLast("/")
                val nameWithoutExt = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
                return "$MEDIA_BASE$modeFolder/$nameWithoutExt.svg"
            }
            return clean
        }

        val baseName = if (clean.contains(".")) clean.substringBeforeLast(".") else clean
        return "$MEDIA_BASE$modeFolder/$baseName.svg"
    }
}

private const val PAGE_SIZE = 20

// ── Data models ───────────────────────────────────────────────────────────────

data class ApiCategory(
    val id: Int,
    val nameAr: String,
    val nameEn: String? = null,
    val iconName: String? = null,
    val subCategories: List<ApiSubCategory> = emptyList()
) {
    fun iconUrl(isDark: Boolean): String? = MediaUrlHelper.getIconUrl(iconName, isDark)
    fun iconUrl(context: Context): String? {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return iconUrl(isDark)
    }
    val iconUrl: String? get() = MediaUrlHelper.getIconUrl(iconName, false)
}

data class ApiSubCategory(
    val id: Int,
    val nameAr: String,
    val nameEn: String? = null,
    val iconName: String? = null,
    val filterOptions: List<ApiFilterOption> = emptyList()
) {
    fun iconUrl(isDark: Boolean): String? = MediaUrlHelper.getIconUrl(iconName, isDark)
    fun iconUrl(context: Context): String? {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return iconUrl(isDark)
    }
    val iconUrl: String? get() = MediaUrlHelper.getIconUrl(iconName, false)
}

data class ApiFilterOption(val id: Int, val nameAr: String, val nameEn: String? = null)

/** The backend sends a synthetic "All" entry as the first filter option for most
 * sub-categories (its own real id, e.g. 82 — not a client-side sentinel). It means
 * "don't filter by this dimension," so it should never be sent as filter_option_id
 * (see MainViewModel.fetchListings) and should never be offered as a choice when
 * actually creating a listing (see CategorySelectionActivity) — a listing can't
 * itself be tagged "All". */
fun ApiFilterOption?.isAllOption(): Boolean {
    if (this == null) return false
    val a = nameAr.trim()
    val e = nameEn?.trim()?.lowercase() ?: ""
    return a == "الكل" || a.startsWith("الكل ") || a.startsWith("كل ") || e == "all" || e.startsWith("all ")
}

/** Same guard applied to sub-categories — the API sometimes includes a catch-all
 * "الكل" sub-category that has no meaning for a specific listing being created, and
 * selecting it when browsing means showing all listings across the entire category. */
fun ApiSubCategory?.isAllOption(): Boolean {
    if (this == null) return false
    val a = nameAr.trim()
    val e = nameEn?.trim()?.lowercase() ?: ""
    return a == "الكل" || a.startsWith("الكل ") || a.startsWith("كل ") || e == "all" || e.startsWith("all ")
}

data class RegionItem(val id: Int, val nameAr: String, val nameEn: String? = null)
data class CityItem(val id: Int, val nameAr: String, val nameEn: String? = null, val regionId: Int)

/** Mirrors ApiFilterOption.isAllOption — the API sends a synthetic "all regions"
 * or "all cities" entry that must not be offered when creating an ad. */
fun RegionItem.isAllOption(): Boolean {
    val a = nameAr.trim()
    val e = nameEn?.trim()?.lowercase() ?: ""
    return a == "الكل" || a == "كل المناطق" || a.startsWith("الكل ") || a.startsWith("كل ") || e == "all" || e.startsWith("all ")
}

fun CityItem.isAllOption(): Boolean {
    val a = nameAr.trim()
    val e = nameEn?.trim()?.lowercase() ?: ""
    return a == "الكل" || a == "كل المدن" || a.startsWith("الكل ") || a.startsWith("كل ") || e == "all" || e.startsWith("all ")
}

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
    var catCityItem: CityItem? = null
        private set

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
                // Single bootstrap call: listings + categories (with nested
                // sub_categories/filter_options) + regions (with nested cities).
                val res = apiPublic.getAppData()
                if (res.isSuccessful) {
                    val bodyText = res.body()?.string() ?: ""
                    android.util.Log.d("MainVM", "AppData RAW: $bodyText")

                    val root = JSONObject(bodyText)
                    val data = root.optJSONObject("data") ?: root

                    val catArr = data.optJSONArray("categories") ?: JSONArray()
                    val parsed = parseCategoriesWithSubCategories(catArr)
                    // Robust filter for duplicate Home
                    val filtered = parsed.filter {
                        it.id != 1 && it.id != 0 &&
                        it.nameAr.trim() != "الرئيسية" &&
                        it.nameAr.trim() != "الرئيسيه"
                    }

                    val regionsArr = data.optJSONArray("regions") ?: JSONArray()
                    val (regions, cities) = parseRegions(regionsArr)

                    withContext(Dispatchers.Main) {
                        _categories.value = filtered
                        _regions.value = regions
                        _allCities.value = cities
                        _isBootLoading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isBootLoading.value = false
                        _errorEvent.value = getApplication<Application>().getString(R.string.error_occurred)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Boot error", e)
                withContext(Dispatchers.Main) {
                    _isBootLoading.value = false
                    val app = getApplication<Application>()
                    // Network failures (DNS, timeout, etc.) get the app's existing
                    // "could not connect" copy; anything else (e.g. a malformed
                    // response) falls back to a generic message — either way, never
                    // the raw exception text, which isn't localized or user-friendly.
                    _errorEvent.value = if (e is java.io.IOException) {
                        app.getString(R.string.kt_str_6c8b9134)
                    } else {
                        app.getString(R.string.error_occurred)
                    }
                }
            }
        }
    }

    private fun extractIconName(obj: JSONObject): String? {
        val keys = listOf("icon", "icon_url", "image", "image_url")
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optString(k).trim()
                if (v.isNotEmpty()) return v
            }
        }
        return null
    }

    private fun parseCategoriesWithSubCategories(arr: JSONArray): List<ApiCategory> {
        val list = mutableListOf<ApiCategory>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)

            val subArr = o.optJSONArray("sub_categories") ?: JSONArray()
            val subs = mutableListOf<ApiSubCategory>()
            for (j in 0 until subArr.length()) {
                val s = subArr.getJSONObject(j)

                val optArr = s.optJSONArray("filter_options") ?: JSONArray()
                val opts = mutableListOf<ApiFilterOption>()
                for (k in 0 until optArr.length()) {
                    val fo = optArr.getJSONObject(k)
                    opts.add(ApiFilterOption(fo.getInt("id"), fo.optString("name_ar", ""), fo.optString("name_en", "").ifEmpty { null }))
                }

                subs.add(ApiSubCategory(
                    id = s.getInt("id"),
                    nameAr = s.optString("name_ar", ""),
                    nameEn = s.optString("name_en", "").ifEmpty { null },
                    iconName = extractIconName(s),
                    filterOptions = opts
                ))
            }

            list.add(ApiCategory(
                id = o.getInt("id"),
                nameAr = o.optString("name_ar", ""),
                nameEn = o.optString("name_en", "").ifEmpty { null },
                iconName = extractIconName(o),
                subCategories = subs
            ))
        }
        return list
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectTopCategory(idx: Int) {
        catIdx = idx
        catSubIdx = null
        catExtraIdx = null
        catRegId = null
        catCityId = null
        catCityItem = null
    }

    fun selectSubCategory(subIdx: Int?) {
        catSubIdx = subIdx
        // Default the extras row (e.g. "All / For Sale / For Rent") to its "All" entry
        // when the sub-category provides one, so it shows selected out of the box
        // instead of nothing being highlighted.
        catExtraIdx = defaultExtraIndex(subIdx)
        catType = null
        catRegId = null
        catCityId = null
        catCityItem = null
        fetchListings(reset = true)
    }

    private fun defaultExtraIndex(subIdx: Int?): Int? {
        val cat = _categories.value?.getOrNull(catIdx - 1) ?: return null
        val sub = subIdx?.let { cat.subCategories.getOrNull(it) } ?: return null
        val first = sub.filterOptions.firstOrNull() ?: return null
        return if (first.isAllOption()) 0 else null
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
        catCityItem = null
        if (catIdx > 0) fetchListings(reset = true)
    }

    fun selectCity(city: CityItem?) {
        catCityId = city?.id
        catCityItem = city
        if (catIdx > 0) fetchListings(reset = true)
    }

    fun selectCityById(cityId: Int?) {
        val city = if (cityId != null) {
            _allCities.value?.find { (catRegId == null || it.regionId == catRegId) && it.id == cityId }
        } else null
        selectCity(city)
    }

    fun hasMorePages() = currentPage < lastPage

    fun fetchListings(reset: Boolean = true) {
        val cats = _categories.value ?: return
        if (catIdx == 0 || catIdx > cats.size) return
        if (isFetching) return

        val cat = cats[catIdx - 1]
        val subs = cat.subCategories
        val ss = catSubIdx?.let { subs.getOrNull(it) }
        // "All" means show every ad under this category, not "match this literal synthetic id" —
        // see ApiSubCategory.isAllOption(). Matches iOS resolvedSubCategoryId behavior.
        val subCategoryId = ss?.takeUnless { it.isAllOption() }?.id
        val extras = ss?.filterOptions ?: emptyList()
        val se = catExtraIdx?.let { extras.getOrNull(it) }
        // "All" means show every tag on this row, not "match this literal id" — see
        // ApiFilterOption.isAllOption().
        val filterOptionId = se?.takeUnless { it.isAllOption() }?.id

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
                val targetCity = catCityItem ?: catCityId?.let { id ->
                    _allCities.value?.find { (catRegId == null || it.regionId == catRegId) && it.id == id }
                }
                val cityName = targetCity?.nameAr

                val res = apiPublic.getListingsCombined(
                    page = currentPage,
                    perPage = PAGE_SIZE,
                    categoryId = cat.id,
                    subCategoryId = subCategoryId,
                    filterOptionId = filterOptionId,
                    regionId = catRegId,
                    city = cityName,
                    listingType = catType
                )

                if (res.isSuccessful) {
                    val root = JSONObject(res.body()?.string() ?: "")
                    // GET /listings wraps results as data: { items: [...], pagination: {...} }
                    // (matches ListingsService.swift's DataObj on iOS) — data itself is an
                    // object, not the array directly.
                    val data = root.optJSONObject("data")
                    val arr = data?.optJSONArray("items") ?: JSONArray()
                    val pagination = data?.optJSONObject("pagination")
                    val fetchedLast = pagination?.optInt("last_page", 1) ?: 1
                    val rawListings = parseListings(arr)

                    // Match iOS ListingsService.applyFilter: strictly verify the listing matches the
                    // selected city, preventing combined location strings (e.g. "الرياض / الخرج") or
                    // loose backend LIKE queries from leaking an ad into multiple cities.
                    val result = if (targetCity != null) {
                        rawListings.filter { listing ->
                            matchesCity(listing.city, targetCity)
                        }
                    } else {
                        rawListings
                    }

                    withContext(Dispatchers.Main) {
                        lastPage = fetchedLast
                        val current = if (reset) emptyList() else (_listings.value ?: emptyList())
                        _listings.value = current + result
                    }
                } else if (!reset) {
                    // The request completed but wasn't 2xx. Roll the page counter
                    // back so the next scroll retries this page instead of
                    // skipping it — currentPage was incremented before the call.
                    withContext(Dispatchers.Main) { currentPage-- }
                }
            } catch (_: Exception) {
                if (!reset) withContext(Dispatchers.Main) { currentPage-- }
            } finally {
                // Always clear the loading flags, on every exit path. These used
                // to be cleared only inside the success branch and the catch, so
                // a non-2xx response (500/404) left the spinner running forever.
                withContext(Dispatchers.Main) {
                    _isFirstPageLoading.value = false
                    _isPagingLoading.value = false
                }
                isFetching = false
            }
        }
    }

    private fun matchesCity(listingCity: String?, targetCity: CityItem): Boolean {
        if (listingCity.isNullOrBlank()) return false
        val trimmed = listingCity.trim()
        val actualCity = when {
            trimmed.contains("/") -> trimmed.substringAfterLast("/").trim()
            trimmed.contains("-") -> trimmed.substringAfterLast("-").trim()
            trimmed.contains(",") -> trimmed.substringAfterLast(",").trim()
            else -> trimmed
        }
        val ar = targetCity.nameAr.trim()
        val en = targetCity.nameEn?.trim()
        return actualCity.equals(ar, ignoreCase = true) ||
               (!en.isNullOrEmpty() && actualCity.equals(en, ignoreCase = true))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun citiesForRegion(regionId: Int?): List<CityItem> {
        if (regionId == null) return emptyList()
        return _allCities.value?.filter { it.regionId == regionId } ?: emptyList()
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