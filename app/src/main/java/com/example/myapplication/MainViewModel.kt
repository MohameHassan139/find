package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

import android.content.Context
import android.content.res.Configuration
import com.example.myapplication.data.ListingJson
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

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
    val city: String?,
    val categoryId: Int? = null,
    val subCategoryId: Int? = null,
    val filterOptionId: Int? = null,
    val regionId: Int? = null,
    val description: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MainViewModel(app: Application) : AndroidViewModel(app) {

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

    private var allListingsPool: List<ApiListing> = emptyList()

    private fun boot() {
        _isBootLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // Single bootstrap call: listings + categories (with nested
            // sub_categories/filter_options) + regions (with nested cities).
            when (val res = AppContainer.catalog.appData()) {
                is ApiResult.Success -> try {
                    val data = res.data
                    allListingsPool = ListingJson.parseList(data.optJSONArray("listings"))

                    val catArr = data.optJSONArray("categories") ?: JSONArray()
                    // Robust filter for duplicate Home
                    val filtered = parseCategoriesWithSubCategories(catArr).filter {
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
                        if (catIdx == 0) fetchListings(reset = true)
                    }
                } catch (e: org.json.JSONException) {
                    android.util.Log.e("MainVM", "Malformed app-data", e)
                    showBootError(R.string.error_occurred)
                }
                // No answer at all gets the "could not connect" copy; anything else
                // a generic message — never raw exception text.
                is ApiResult.NetworkError -> showBootError(R.string.error_server_unreachable)
                is ApiResult.HttpError -> showBootError(R.string.error_occurred)
            }
        }
    }

    private suspend fun showBootError(@androidx.annotation.StringRes message: Int) =
        withContext(Dispatchers.Main) {
            _isBootLoading.value = false
            _errorEvent.value = getApplication<Application>().getString(message)
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
        catExtraIdx = extraIdx
        fetchListings(reset = true)
    }

    fun selectType(type: String?) {
        catType = type
        fetchListings(reset = true)
    }

    fun selectRegion(regionId: Int?) {
        catRegId = regionId
        catCityId = null
        catCityItem = null
        fetchListings(reset = true)
    }

    fun selectCity(city: CityItem?) {
        catCityId = city?.id
        catCityItem = city
        fetchListings(reset = true)
    }

    fun hasMorePages() = currentPage < lastPage

    fun fetchListings(reset: Boolean = true) {
        if (isFetching) return

        val isHomeFeed = catIdx == 0
        val cats = _categories.value
        if (!isHomeFeed) {
            if (cats == null || catIdx > cats.size) return
        }

        val cat = if (!isHomeFeed && cats != null) cats[catIdx - 1] else null
        val subs = cat?.subCategories ?: emptyList()
        val ss = catSubIdx?.let { subs.getOrNull(it) }
        // "All" means show every ad under this category, not "match this literal synthetic id" —
        // see ApiSubCategory.isAllOption(). Matches iOS resolvedSubCategoryId behavior.
        val subCategoryId = if (isHomeFeed) null else ss?.takeUnless { it.isAllOption() }?.id
        val extras = ss?.filterOptions ?: emptyList()
        val se = catExtraIdx?.let { extras.getOrNull(it) }
        val isExtraAll = isHomeFeed || se == null || se.isAllOption()

        // When sub-category is "All" (null), filter_option_id belongs to the synthetic
        // "All" sub-category (e.g. 104) which no database listing has; so pass null to
        // fetch all category listings and filter them client-side.
        val apiFilterOptionId = if (subCategoryId != null && !isExtraAll) se?.id else null

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
                val cityName = targetCity?.takeUnless { it.isAllOption() }?.nameAr

                val res = AppContainer.catalog.listings(
                    page = currentPage,
                    perPage = PAGE_SIZE,
                    categoryId = cat?.id,
                    subCategoryId = subCategoryId,
                    filterOptionId = apiFilterOptionId,
                    regionId = catRegId,
                    city = cityName,
                    listingType = catType
                )

                if (res is ApiResult.Success) {
                    val fetchedLast = res.data.lastPage
                    val rawListings = res.data.items

                    // Merge into pool
                    val knownIds = allListingsPool.map { it.id }.toSet()
                    val toAdd = rawListings.filter { it.id !in knownIds }
                    if (toAdd.isNotEmpty()) {
                        allListingsPool = allListingsPool + toAdd
                    }

                    var sourceList = rawListings
                    // Fallback to local pool if server returned empty due to unpopulated filter_option_id
                    if (sourceList.isEmpty() && !isExtraAll && reset && cat != null) {
                        sourceList = allListingsPool.filter {
                            it.categoryId == cat.id && (subCategoryId == null || it.subCategoryId == subCategoryId)
                        }
                    } else if (sourceList.isEmpty() && isHomeFeed && reset && catRegId == null && catCityId == null && catType.isNullOrBlank()) {
                        sourceList = allListingsPool
                    }

                    // Strictly verify region, city, listingType, and filterOption,
                    // preventing loose backend LIKE queries, cross-region leaks, or mixed types.
                    var filtered = sourceList
                    if (catRegId != null) {
                        filtered = filtered.filter { matchesRegion(it, catRegId!!) }
                    }
                    if (targetCity != null && !targetCity.isAllOption()) {
                        filtered = filtered.filter { matchesCity(it.city, targetCity) }
                    }
                    if (!catType.isNullOrBlank()) {
                        filtered = filtered.filter { it.listingType?.equals(catType, ignoreCase = true) == true }
                    }
                    if (!isExtraAll && se != null && cat != null) {
                        filtered = filtered.filter { matchesFilterOption(it, se, cat) }
                    }
                    val result = filtered

                    withContext(Dispatchers.Main) {
                        lastPage = fetchedLast
                        val current = if (reset) emptyList() else (_listings.value ?: emptyList())
                        val total = current + result
                        _listings.value = total
                        _isEmptyState.value = total.isEmpty()
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
                // Always clear the loading flags, on every exit path.
                withContext(Dispatchers.Main) {
                    _isFirstPageLoading.value = false
                    _isPagingLoading.value = false
                }
                isFetching = false
            }
        }
    }

    private fun matchesFilterOption(
        listing: ApiListing,
        opt: ApiFilterOption?,
        cat: ApiCategory
    ): Boolean {
        if (opt == null || opt.isAllOption()) return true

        // 1. Direct ID match if present
        if (listing.filterOptionId != null && listing.filterOptionId == opt.id) return true

        val optNameAr = opt.nameAr.trim()
        val optNameEn = opt.nameEn?.trim()?.lowercase() ?: ""

        // 2. Real estate (عقارات) special handling for "للبيع" and "للإيجار"
        val isSale = optNameAr.contains("بيع") || optNameEn.contains("sale")
        val isRent = optNameAr.contains("إيجار") || optNameAr.contains("ايجار") || optNameEn.contains("rent")

        if (isSale || isRent) {
            val allMatchingIds = cat.subCategories.flatMap { it.filterOptions }
                .filter {
                    if (isSale) (it.nameAr.contains("بيع") || it.nameEn?.contains("sale", ignoreCase = true) == true)
                    else (it.nameAr.contains("إيجار") || it.nameAr.contains("ايجار") || it.nameEn?.contains("rent", ignoreCase = true) == true)
                }
                .map { it.id }
                .toSet()

            if (listing.filterOptionId != null && listing.filterOptionId in allMatchingIds) {
                return true
            }

            val text = "${listing.title.orEmpty()} ${listing.description.orEmpty()}"
            val subName = cat.subCategories.find { it.id == listing.subCategoryId }?.nameAr ?: ""
            val fullText = "$text $subName"

            val hasSaleKeyword = fullText.contains("للبيع") || fullText.contains("البيع") || fullText.contains("بيع")
            val hasRentKeyword = fullText.contains("للإيجار") || fullText.contains("للايجار") || fullText.contains("إيجار") || fullText.contains("ايجار")

            if (isSale) {
                if (hasRentKeyword && !hasSaleKeyword) return false
                return hasSaleKeyword || (!hasRentKeyword && listing.price != null && listing.price > 50000)
            } else {
                return hasRentKeyword
            }
        }

        // 3. For any other category (e.g. car brand names like "تويوتا")
        val text = "${listing.title.orEmpty()} ${listing.description.orEmpty()}".lowercase()
        if (optNameAr.isNotEmpty() && text.contains(optNameAr.lowercase())) return true
        if (optNameEn.isNotEmpty() && text.contains(optNameEn)) return true

        return false
    }

    private fun matchesRegion(listing: ApiListing, targetRegionId: Int): Boolean {
        // 1. Direct regionId match if present
        if (listing.regionId != null && listing.regionId > 0) {
            return listing.regionId == targetRegionId
        }

        // 2. Check regionNameAr against the target region's names
        val targetRegion = _regions.value?.find { it.id == targetRegionId }
        if (targetRegion != null && !listing.regionNameAr.isNullOrBlank()) {
            val regName = listing.regionNameAr.trim()
            val targetAr = targetRegion.nameAr.trim()
            val targetEn = targetRegion.nameEn?.trim() ?: ""
            val cleanTargetAr = targetAr.removePrefix("منطقة ").trim()
            val cleanRegName = regName.removePrefix("منطقة ").trim()
            if (regName.equals(targetAr, ignoreCase = true) ||
                cleanRegName.equals(cleanTargetAr, ignoreCase = true) ||
                (targetEn.isNotEmpty() && regName.equals(targetEn, ignoreCase = true))) {
                return true
            }
        }

        // 3. Check if the listing's city belongs to the target region
        if (!listing.city.isNullOrBlank()) {
            val citiesInTargetRegion = citiesForRegion(targetRegionId)
            val matchedCityInRegion = citiesInTargetRegion.any { cityItem ->
                !cityItem.isAllOption() && matchesCity(listing.city, cityItem)
            }
            if (matchedCityInRegion) {
                return true
            }

            // If the listing's city belongs to ANY other known region, it definitely does not match
            val allOtherCities = _allCities.value?.filter { it.regionId != targetRegionId && !it.isAllOption() } ?: emptyList()
            if (allOtherCities.any { matchesCity(listing.city, it) }) {
                return false
            }
        }

        return false
    }

    private fun matchesCity(listingCity: String?, targetCity: CityItem): Boolean {
        if (listingCity.isNullOrBlank()) return false
        val trimmed = listingCity.trim()
        val ar = targetCity.nameAr.trim()
        val en = targetCity.nameEn?.trim()

        if (ar.isEmpty() || targetCity.isAllOption()) return true

        // 1. Direct exact match
        if (trimmed.equals(ar, ignoreCase = true) || (!en.isNullOrEmpty() && trimmed.equals(en, ignoreCase = true))) {
            return true
        }

        // 2. Tokenized match (split by / - , or Arabic comma)
        val parts = trimmed.split('/', '-', ',', '،').map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            if (part.equals(ar, ignoreCase = true) || (!en.isNullOrEmpty() && part.equals(en, ignoreCase = true))) {
                return true
            }
        }

        // 3. Substring match for combined strings like "منطقة مكة المكرمة مكة المكرمة"
        if (trimmed.contains(ar, ignoreCase = true) || (!en.isNullOrEmpty() && trimmed.contains(en, ignoreCase = true))) {
            return true
        }

        return false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun citiesForRegion(regionId: Int?): List<CityItem> {
        if (regionId == null) return emptyList()
        val list = _allCities.value?.filter { it.regionId == regionId } ?: emptyList()
        if (list.isNotEmpty() && list.none { it.isAllOption() }) {
            return listOf(CityItem(0, "كل المدن", "All Cities", regionId)) + list
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
}