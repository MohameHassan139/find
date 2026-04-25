package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapters.CategoryGridAdapter
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.adapters.SubCategoryGridAdapter
import com.example.myapplication.adapters.TopTabAdapter
import com.example.myapplication.adapters.SubTabAdapter
import com.example.myapplication.adapters.ExtraTabAdapter
import com.example.myapplication.auth.AuthRetrofitClient
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.AuthGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    private lateinit var categoryAdapter: CategoryGridAdapter
    private lateinit var subCategoryAdapter: SubCategoryGridAdapter
    private lateinit var listingsAdapter: ListingsAdapter
    private lateinit var topTabAdapter: TopTabAdapter
    private lateinit var subTabAdapter: SubTabAdapter
    private lateinit var extraTabAdapter: ExtraTabAdapter

    private var suppressSpinner = false
    private var lastRegionList: List<RegionItem> = emptyList()
    private var regionItems: List<RegionItem> = emptyList()

    private val gray = "#888888".toColorInt()
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets(
            appBarId = R.id.llAppBar,
            bottomNavId = R.id.cvBottomNav
        )

        setupAdapters()
        setupTypeChips()
        observeViewModel()
        setupNavigation()
        setupSearch()
        refreshUserProfile()
        handleIncomingCategoryIntent(intent)
    }

    private fun setupSearch() {
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false

        val searchIntent = Intent(this, SearchActivity::class.java)
        binding.llSearchContainer.setOnClickListener {
            startActivity(searchIntent)
        }
        binding.etSearch.setOnClickListener {
            startActivity(searchIntent)
        }
    }

    /** Handle incoming category selection from HomeHeaderHelper on other screens */
    private fun handleIncomingCategoryIntent(intent: Intent) {
        val catIdx = intent.getIntExtra(EXTRA_CATEGORY_IDX, -1)
        if (catIdx < 0) return
        val cats = vm.categories.value ?: return
        if (catIdx == 0) {
            vm.selectTopCategory(0)
            topTabAdapter.update(cats, 0)
            categoryAdapter.updateData(cats)
            binding.rvSubTabs.visibility    = View.GONE
            binding.rvExtraTabs.visibility  = View.GONE
            binding.llFilterBar.visibility  = View.GONE
            binding.llRegionRow.visibility  = View.GONE
            applyBodyState(BodyState.CATEGORIES)
        } else {
            val cat = cats.getOrNull(catIdx - 1) ?: return
            openCategory(cat)
        }
    }

    private fun refreshUserProfile() {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.getMe("Bearer $token")
                if (response.isSuccessful) {
                    val user = response.body()?.user ?: return@launch
                    TokenManager.save(
                        this@MainActivity,
                        token,
                        user.name ?: TokenManager.getName(this@MainActivity),
                        user.phone ?: TokenManager.getPhone(this@MainActivity),
                        user.avatar ?: TokenManager.getAvatar(this@MainActivity),
                        user.id.toString()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Boot error", e)
                withContext(Dispatchers.Main) {
                    vm.setError("خطأ في الإقلاع: ${e.javaClass.simpleName} - ${e.localizedMessage}")
                }
            }
        }
    }

    private fun setupAdapters() {
        categoryAdapter = CategoryGridAdapter(emptyList()) { cat ->
            openCategory(cat)
        }
        binding.rvCategoryGrid.layoutManager = GridLayoutManager(this, 3)
        binding.rvCategoryGrid.adapter = categoryAdapter

        subCategoryAdapter = SubCategoryGridAdapter(emptyList()) { sub ->
            val cats = vm.categories.value ?: return@SubCategoryGridAdapter
            
            // Try to get parent from current selection first (optimization)
            var parentCat = if (vm.catIdx > 0) cats.getOrNull(vm.catIdx - 1) else null
            if (parentCat == null || parentCat.subCategories.none { it.id == sub.id }) {
                parentCat = cats.firstOrNull { c -> c.subCategories.any { it.id == sub.id } }
            }
            
            if (parentCat == null) return@SubCategoryGridAdapter
            
            val parentIdx = cats.indexOf(parentCat) + 1
            vm.selectTopCategory(parentIdx)
            vm.selectSubCategory(parentCat.subCategories.indexOf(sub))
            
            topTabAdapter.update(cats, parentIdx)
            buildSubTabs(parentCat)
            showListingsMode()
        }
        binding.rvSubCategoryGrid.layoutManager = GridLayoutManager(this, 3)
        binding.rvSubCategoryGrid.adapter = subCategoryAdapter

        listingsAdapter = ListingsAdapter(
            items = emptyList(),
            onClick = { listing ->
                val intent = Intent(this, ListingDetailActivity::class.java)
                intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listing.id)
                startActivity(intent)
            },
            onFavoriteClick = { listing, isFav ->
                toggleFavorite(listing.id, isFav)
            }
        )
        binding.rvListings.layoutManager = LinearLayoutManager(this)
        binding.rvListings.adapter = listingsAdapter
        binding.rvListings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(1)) vm.fetchListings(false)
            }
        })

        topTabAdapter = TopTabAdapter(emptyList(), 0) { cat ->
            val cats = vm.categories.value ?: emptyList()
            val idx = if (cat == null) 0 else (cats.indexOf(cat) + 1)
            if (idx == 0) {
                vm.selectTopCategory(0)
                categoryAdapter.updateData(cats)
                binding.rvSubTabs.visibility    = View.GONE
                binding.rvExtraTabs.visibility  = View.GONE
                binding.llFilterBar.visibility  = View.GONE
                binding.llRegionRow.visibility  = View.GONE
                applyBodyState(BodyState.CATEGORIES)
            } else {
                openCategory(cat!!)
            }
            topTabAdapter.update(cats, idx)
        }
        binding.rvTopTabs.adapter = topTabAdapter

        subTabAdapter = SubTabAdapter(emptyList(), null) { sub ->
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1) ?: return@SubTabAdapter
            val pos = cat.subCategories.indexOf(sub)
            vm.selectSubCategory(pos)
            buildSubTabs(cat)
            showListingsMode()
        }
        binding.rvSubTabs.adapter = subTabAdapter

        extraTabAdapter = ExtraTabAdapter(emptyList(), null) { opt ->
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1) ?: return@ExtraTabAdapter
            val ss = vm.catSubIdx?.let { cat.subCategories.getOrNull(it) } ?: return@ExtraTabAdapter
            val pos = ss.filterOptions.indexOf(opt)
            vm.selectExtra(pos)
            showListingsMode()
            buildExtraTabs(ss)
        }
        binding.rvExtraTabs.adapter = extraTabAdapter
    }

    private fun setupTypeChips() {
        binding.chipAll.setOnClickListener {
            vm.selectType(null)
            updateChipStyles(null)
            resetCityFilter()
            showRegionRow()
        }
        binding.chipOffer.setOnClickListener {
            vm.selectType("offer")
            updateChipStyles("offer")
            resetCityFilter()
            showRegionRow()
        }
        binding.chipRequest.setOnClickListener {
            vm.selectType("request")
            updateChipStyles("request")
            resetCityFilter()
            showRegionRow()
        }
    }

    private fun resetCityFilter() {
        // Reset city selection and hide city pill
        binding.spinnerCity.visibility = View.GONE
        vm.selectCity(null)
        resetCityPill()
    }

    private fun showRegionRow() {
        binding.llRegionRow.visibility = View.VISIBLE
    }

    private fun updateChipStyles(active: String?) {
        fun style(tv: TextView, isActive: Boolean) {
            tv.setBackgroundResource(if (isActive) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            tv.setTextColor(if (isActive) getColor(R.color.white) else getColor(R.color.find_gray_text))
            tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        style(binding.chipAll, active == null)
        style(binding.chipOffer, active == "offer")
        style(binding.chipRequest, active == "request")
    }

    private enum class BodyState { CATEGORIES, SUBCATEGORIES, GRID_LOADING, LOADING, ADS, EMPTY }

    private fun applyBodyState(state: BodyState) {
        binding.rvCategoryGrid.visibility    = if (state == BodyState.CATEGORIES)    View.VISIBLE else View.GONE
        binding.rvSubCategoryGrid.visibility = if (state == BodyState.SUBCATEGORIES) View.VISIBLE else View.GONE
        binding.pbHomeGrid.visibility        = if (state == BodyState.GRID_LOADING)  View.VISIBLE else View.GONE
        binding.llListingsShimmer.visibility = if (state == BodyState.LOADING)       View.VISIBLE else View.GONE
        binding.rvListings.visibility        = if (state == BodyState.ADS)           View.VISIBLE else View.GONE
        binding.llEmptyState.visibility      = if (state == BodyState.EMPTY)         View.VISIBLE else View.GONE
        if (state == BodyState.LOADING) animateShimmer(binding.llListingsShimmer)
    }

    private fun observeViewModel() {
        vm.isBootLoading.observe(this) { loading ->
            binding.bootShimmer.visibility = if (loading) View.VISIBLE else View.GONE
            binding.rvTopTabs.visibility  = if (loading) View.GONE   else View.VISIBLE
        }

        vm.categories.observe(this) { cats ->
            android.util.Log.d("MainActivity", "Received ${cats.size} categories")
            topTabAdapter.update(cats, vm.catIdx)
            
            val pending = pendingCategoryId
            if (pending != null) {
                val cat = cats.find { it.id == pending }
                if (cat != null && cat.subCategories.isNotEmpty()) {
                    pendingCategoryId = null
                    isShowingSubGrid = true
                    subCategoryAdapter.updateData(cat.subCategories)
                    buildSubTabs(cat)
                    applyBodyState(BodyState.SUBCATEGORIES)
                    return@observe
                }
            }
            if (vm.catIdx == 0) {
                categoryAdapter.updateData(cats)
                applyBodyState(BodyState.CATEGORIES)
            } else {
                // If we are inside a category, and data just arrived/updated, refresh sub-grid
                val currentCat = cats.getOrNull(vm.catIdx - 1)
                if (currentCat != null) {
                    subCategoryAdapter.updateData(currentCat.subCategories)
                    buildSubTabs(currentCat)
                    applyBodyState(BodyState.SUBCATEGORIES) // Ensure it's visible!
                }
            }
        }

        vm.regions.observe(this) { regions -> buildRegionSpinner(regions) }
        vm.isHomeGridLoading.observe(this) { loading -> if (loading) applyBodyState(BodyState.GRID_LOADING) }

        vm.homeSubCategories.observe(this) { subs ->
            if (subs.isEmpty()) {
                if (vm.catIdx == 0) {
                   categoryAdapter.updateData(vm.categories.value ?: emptyList())
                   applyBodyState(BodyState.CATEGORIES)
                }
            } else {
                subCategoryAdapter.updateData(subs)
                applyBodyState(BodyState.SUBCATEGORIES)
            }
        }

        vm.isFirstPageLoading.observe(this) { loading ->
            if (loading && pendingCategoryId == null && !isShowingSubGrid) applyBodyState(BodyState.LOADING)
        }

        vm.isPagingLoading.observe(this) { loading -> listingsAdapter.setFooterLoading(loading) }
        vm.listings.observe(this) { listings ->
            listingsAdapter.updateData(listings)
            if (!isShowingSubGrid) {
                if (listings.isNotEmpty()) applyBodyState(BodyState.ADS)
                else applyBodyState(BodyState.EMPTY)
            }
        }

        vm.isEmptyState.observe(this) { empty ->
            if (empty && pendingCategoryId == null && !isShowingSubGrid) applyBodyState(BodyState.EMPTY)
        }

        vm.errorEvent.observe(this) { msg ->
            if (msg != null) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("خطأ في البيانات")
                    .setMessage(msg)
                    .setPositiveButton("موافق") { d, _ -> d.dismiss() }
                    .show()
            }
        }

        binding.btnResetFilters.setOnClickListener {
            vm.selectTopCategory(0)
            updateChipStyles(null)
            val cats = vm.categories.value ?: emptyList()
            topTabAdapter.update(cats, 0)
            categoryAdapter.updateData(cats)
            binding.rvSubTabs.visibility    = View.GONE
            binding.rvExtraTabs.visibility  = View.GONE
            binding.llFilterBar.visibility  = View.GONE
            binding.llRegionRow.visibility  = View.GONE
            applyBodyState(BodyState.CATEGORIES)
        }
    }

    private fun openCategory(cat: ApiCategory) {
        val cats = vm.categories.value ?: return
        val pos = cats.indexOf(cat)
        vm.selectTopCategory(pos + 1)
        topTabAdapter.update(cats, pos + 1)
        
        isShowingSubGrid = true
        subCategoryAdapter.updateData(cat.subCategories)
        buildSubTabs(cat)
        applyBodyState(BodyState.SUBCATEGORIES)
    }

    private fun showListingsMode() {
        isShowingSubGrid = false
        // Show chips row, hide region row until a chip is tapped
        binding.llFilterBar.visibility = View.VISIBLE
        binding.llRegionRow.visibility = View.GONE
        updateChipStyles(vm.catType)
        applyBodyState(BodyState.LOADING)
        vm.fetchListings()
    }

    private fun buildSubTabs(cat: ApiCategory) {
        val subs = cat.subCategories
        if (subs.isEmpty()) {
            binding.rvSubTabs.visibility   = View.GONE
            binding.rvExtraTabs.visibility = View.GONE
            // No sub-list → show chips immediately
            binding.llFilterBar.visibility  = View.VISIBLE
            binding.llRegionRow.visibility  = View.GONE
            return
        }
        binding.rvSubTabs.visibility   = View.VISIBLE
        binding.llFilterBar.visibility  = View.GONE   // hide chips while sub-grid is showing
        binding.llRegionRow.visibility  = View.GONE
        subTabAdapter.update(subs, vm.catSubIdx)

        val selectedSub = vm.catSubIdx?.let { subs.getOrNull(it) }
        buildExtraTabs(selectedSub)
    }

    private fun buildExtraTabs(sub: ApiSubCategory?) {
        val extras = sub?.filterOptions ?: emptyList()
        if (extras.isEmpty()) {
            binding.rvExtraTabs.visibility = View.GONE
            return
        }
        binding.rvExtraTabs.visibility = View.VISIBLE
        extraTabAdapter.update(extras, vm.catExtraIdx)
    }

    private fun buildRegionSpinner(regions: List<RegionItem>) {
        if (regions == lastRegionList) return
        lastRegionList = regions
        regionItems = regions
        resetRegionPill()
        vm.selectRegion(null)
        binding.spinnerCity.visibility = View.GONE

        binding.spinnerRegion.setOnClickListener {
            val popup = android.widget.PopupMenu(this, binding.spinnerRegion)
            regions.forEachIndexed { i, r ->
                popup.menu.add(0, i, i, LocaleHelper.localizedName(this, r.nameAr, r.nameEn))
            }
            popup.setOnMenuItemClickListener { item ->
                val region = regions[item.itemId]
                setRegionPillActive(LocaleHelper.localizedName(this, region.nameAr, region.nameEn))
                vm.selectRegion(region.id)
                buildCityDropdown(region.id)
                true
            }
            popup.show()
        }
    }

    private fun buildCityDropdown(regionId: Int?) {
        val cities = vm.citiesForRegion(regionId)
        if (regionId == null || cities.isEmpty()) {
            binding.spinnerCity.visibility = View.GONE
            return
        }
        binding.spinnerCity.visibility = View.VISIBLE
        resetCityPill()
        vm.selectCity(null)

        binding.spinnerCity.setOnClickListener {
            val popup = android.widget.PopupMenu(this, binding.spinnerCity)
            cities.forEachIndexed { i, c ->
                popup.menu.add(0, i, i, LocaleHelper.localizedName(this, c.nameAr, c.nameEn))
            }
            popup.setOnMenuItemClickListener { item ->
                val city = cities[item.itemId]
                setCityPillActive(LocaleHelper.localizedName(this, city.nameAr, city.nameEn))
                vm.selectCity(city.id)
                true
            }
            popup.show()
        }
    }

    // ── Pill highlight helpers ────────────────────────────────────────────────

    private fun resetRegionPill() {
        binding.tvRegionLabel.text = getString(R.string.all_regions)
        binding.tvRegionLabel.setTextColor(getColor(R.color.find_gray_text))
        binding.spinnerRegion.setBackgroundResource(R.drawable.bg_chip_unselected)
    }

    private fun setRegionPillActive(label: String) {
        binding.tvRegionLabel.text = label
        binding.tvRegionLabel.setTextColor(getColor(R.color.white))
        binding.spinnerRegion.setBackgroundResource(R.drawable.bg_chip_selected)
    }

    private fun resetCityPill() {
        binding.tvCityLabel.text = getString(R.string.all_cities)
        binding.tvCityLabel.setTextColor(getColor(R.color.find_gray_text))
        binding.spinnerCity.setBackgroundResource(R.drawable.bg_chip_unselected)
    }

    private fun setCityPillActive(label: String) {
        binding.tvCityLabel.text = label
        binding.tvCityLabel.setTextColor(getColor(R.color.white))
        binding.spinnerCity.setBackgroundResource(R.drawable.bg_chip_selected)
    }

    private var pendingCategoryId: Int? = null
    private var isShowingSubGrid = false

    companion object {
        const val EXTRA_CATEGORY_IDX = "extra_category_idx"
    }

    private fun animateShimmer(view: View) {
        android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.4f, 1f, 0.4f).apply {
            duration = 1200
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
    }

    private fun setupNavigation() {
        BottomNavHelper.setup(this, NavScreen.HOME)
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.slide_in_left, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        BottomNavHelper.setup(this, NavScreen.HOME)
        loadFavoriteIds()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCategoryIntent(intent)
    }

    private fun loadFavoriteIds() {
        if (!TokenManager.isLoggedIn(this)) return
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.build(this@MainActivity)
                val response = withContext(Dispatchers.IO) { api.getFavorites() }
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: return@launch
                    val ids = mutableSetOf<String>()
                    val root = org.json.JSONObject(body)
                    val arr = root.optJSONArray("data") ?: return@launch
                    for (i in 0 until arr.length()) {
                        // data[] items ARE the listing directly per API spec
                        val id = arr.getJSONObject(i).optString("id")
                        if (id.isNotEmpty()) ids.add(id)
                    }
                    listingsAdapter.setFavoriteIds(ids)
                }
            } catch (_: Exception) {}
        }
    }

    private fun toggleFavorite(listingId: String, add: Boolean) {
        AuthGuard.requireLogin(this) {
            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.build(this@MainActivity)
                    withContext(Dispatchers.IO) {
                        if (add) api.addFavorite(com.example.myapplication.favorites.AddFavoriteRequest(listingId))
                        else api.removeFavorite(listingId)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
