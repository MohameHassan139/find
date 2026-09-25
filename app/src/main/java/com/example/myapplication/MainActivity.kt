package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.myapplication.adapters.CategoryGridAdapter
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.adapters.SubCategoryGridAdapter
import com.example.myapplication.adapters.TopTabAdapter
import com.example.myapplication.adapters.SubTabAdapter
import com.example.myapplication.adapters.ExtraTabAdapter
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.push.PushTokenManager
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.AuthGuard
import com.example.myapplication.widgets.StrokeTextView
import com.google.android.material.appbar.AppBarLayout
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
    private var pendingCategoryId: Int? = null
    private var isShowingSubGrid = false

    private val gray = "#888888".toColorInt()
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way — push is a non-critical enhancement */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets(
            appBarId = R.id.llAppBar,
            bottomNavId = R.id.cvBottomNav
        )

        if (savedInstanceState != null) {
            isShowingSubGrid = savedInstanceState.getBoolean(KEY_IS_SHOWING_SUB_GRID, false)
        } else if (vm.catIdx > 0 && vm.catSubIdx == null) {
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1)
            isShowingSubGrid = (cat != null && cat.subCategories.isNotEmpty())
        }

        setupAdapters()
        setupTypeChips()
        observeViewModel()
        setupNavigation()
        setupSearch()
        refreshUserProfile()
        requestNotificationPermissionIfNeeded()
        lifecycleScope.launch { PushTokenManager.refreshAndUploadIfNeeded(this@MainActivity) }
        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }
        handleIncomingCategoryIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupSearch() {
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false

        val searchIntent = Intent(this, SearchActivity::class.java)
        binding.llSearchContainer.setOnClickListener {
            startWithPush(searchIntent)
        }
        binding.etSearch.setOnClickListener {
            startWithPush(searchIntent)
        }
    }

    /** Handle incoming category selection from HomeHeaderHelper on other screens */
    private fun handleIncomingCategoryIntent(intent: Intent) {
        val catIdx = intent.getIntExtra(EXTRA_CATEGORY_IDX, -1)
        if (catIdx < 0) return
        val cats = vm.categories.value ?: return
        if (catIdx == 0) {
            resetToHome()
        } else {
            val cat = cats.getOrNull(catIdx - 1) ?: return
            openCategory(cat)
        }
    }

    private fun refreshUserProfile() {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@MainActivity).getMe()
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
        binding.rvCategoryGrid.tuneForSmoothScrolling()

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
        binding.rvSubCategoryGrid.tuneForSmoothScrolling()

        listingsAdapter = ListingsAdapter(
            items = emptyList(),
            onClick = { listing ->
                val intent = Intent(this, ListingDetailActivity::class.java)
                intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listing.id)
                val allIds = ArrayList(listingsAdapter.getItems().map { it.id })
                val index = allIds.indexOf(listing.id)
                intent.putStringArrayListExtra(ListingDetailActivity.EXTRA_SIBLING_IDS, allIds)
                intent.putExtra(ListingDetailActivity.EXTRA_CURRENT_INDEX, index)
                startActivity(intent)
            },
            onFavoriteClick = { listing, isFav ->
                toggleFavorite(listing.id, isFav)
            }
        )
        binding.rvListings.layoutManager = LinearLayoutManager(this)
        binding.rvListings.adapter = listingsAdapter
        binding.rvListings.tuneForSmoothScrolling()

        // Infinite scroll: prefetch the next page a few rows *before* the end so
        // new cards are already there when the user reaches them (no stall at the bottom).
        binding.rvListings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) maybeLoadNextPage()
            }
        })

        applyContentBottomInset()

        topTabAdapter = TopTabAdapter(emptyList(), 0) { cat ->
            val cats = vm.categories.value ?: emptyList()
            val idx = if (cat == null) 0 else (cats.indexOf(cat) + 1)
            if (idx == 0) {
                resetToHome()
            } else {
                openCategory(cat!!)
            }
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
            if (pos >= 0) {
                vm.selectExtra(pos)
                extraTabAdapter.update(ss.filterOptions, pos)
                applyBodyState(BodyState.LOADING)
            }
        }
        binding.rvExtraTabs.adapter = extraTabAdapter
    }

    private fun resetFilterStripsScrollState() {
        binding.appBarFilters.setExpanded(true, false)
    }

    /** Native, recycling, jank-free scrolling for the content lists. */
    private fun RecyclerView.tuneForSmoothScrolling() {
        isNestedScrollingEnabled = true          // drives the collapsing filter strips
        setHasFixedSize(true)                    // size is fixed by the parent, not the items
        setItemViewCacheSize(12)                 // keep recently-scrolled-off rows bound
        // No cross-fade "blink" when an item is re-bound (favorite toggle, page append)
        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    /** Jump every content list back to the top and re-reveal the filter strips. */
    private fun scrollContentToTop() {
        binding.rvListings.stopScroll()
        binding.rvListings.scrollToPosition(0)
        binding.rvCategoryGrid.scrollToPosition(0)
        binding.rvSubCategoryGrid.scrollToPosition(0)
        resetFilterStripsScrollState()
    }

    private fun maybeLoadNextPage() {
        if (binding.rvListings.visibility != View.VISIBLE) return
        if (!vm.hasMorePages()) return
        val lm = binding.rvListings.layoutManager as? LinearLayoutManager ?: return
        val total = lm.itemCount
        if (total == 0) return
        if (lm.findLastVisibleItemPosition() >= total - PREFETCH_DISTANCE) {
            vm.fetchListings(false)
        }
    }

    /**
     * Lists scroll *under* the floating bottom nav. Pad their bottom by the nav's
     * real footprint (height + margin, which already includes the system nav-bar
     * inset) so the last card can always be scrolled fully into view.
     */
    private fun applyContentBottomInset() {
        val nav = binding.cvBottomNav.root
        val extra = (16 * resources.displayMetrics.density).toInt()
        nav.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val margin = (v.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            val bottom = v.height + margin + extra
            listOf(binding.rvListings, binding.rvCategoryGrid, binding.rvSubCategoryGrid).forEach { rv ->
                if (rv.paddingBottom != bottom) {
                    rv.setPadding(rv.paddingLeft, rv.paddingTop, rv.paddingRight, bottom)
                }
            }
        }
    }

    private fun setupTypeChips() {
        binding.llChipAll.setOnClickListener {
            vm.selectType(null)
            updateChipStyles(null)
            applyBodyState(BodyState.LOADING)
        }
        binding.llChipOffer.setOnClickListener {
            vm.selectType("offer")
            updateChipStyles("offer")
            applyBodyState(BodyState.LOADING)
        }
        binding.llChipRequest.setOnClickListener {
            vm.selectType("request")
            updateChipStyles("request")
            applyBodyState(BodyState.LOADING)
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
        fun style(tv: TextView, underline: View, isActive: Boolean) {
            // Same two sizes as the category / sub-category / option rows
            tv.textSize = if (isActive) com.example.myapplication.adapters.TAB_TEXT_SIZE_ACTIVE
                          else com.example.myapplication.adapters.TAB_TEXT_SIZE_INACTIVE
            if (tv is StrokeTextView) {
                tv.applyTabState(isActive)
            } else {
                tv.setTypeface(FindFonts.typeface(tv.context), if (isActive) Typeface.BOLD else Typeface.NORMAL)
                tv.setTextColor(if (isActive) getColor(R.color.tab_label_active) else getColor(R.color.tab_label_inactive))
            }
            // Rounded blue indicator in dark mode, flat bar in light (see bg_tab_underline_active)
            underline.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
            if (isActive) underline.setBackgroundResource(R.drawable.bg_tab_underline_active)
            else underline.background = null
        }
        style(binding.chipAll, binding.underlineAll, active == null)
        style(binding.chipOffer, binding.underlineOffer, active == "offer")
        style(binding.chipRequest, binding.underlineRequest, active == "request")
    }

    private enum class BodyState { CATEGORIES, SUBCATEGORIES, GRID_LOADING, LOADING, ADS, EMPTY }

    private fun applyBodyState(state: BodyState) {
        binding.rvCategoryGrid.visibility    = if (state == BodyState.CATEGORIES)    View.VISIBLE else View.GONE
        binding.rvSubCategoryGrid.visibility = if (state == BodyState.SUBCATEGORIES) View.VISIBLE else View.GONE
        binding.pbHomeGrid.visibility        = if (state == BodyState.GRID_LOADING)  View.VISIBLE else View.GONE
        binding.llListingsShimmer.visibility = if (state == BodyState.LOADING)       View.VISIBLE else View.GONE
        binding.rvListings.visibility        = if (state == BodyState.ADS)           View.VISIBLE else View.GONE
        binding.llEmptyState.visibility      = if (state == BodyState.EMPTY)         View.VISIBLE else View.GONE
        if (state == BodyState.LOADING) {
            // A new query is loading — its results must start from the top.
            binding.rvListings.stopScroll()
            binding.rvListings.scrollToPosition(0)
            startShimmer()
        } else {
            stopShimmer()
        }

        val showBackToParent = (state == BodyState.EMPTY) && (vm.catIdx > 0) && (vm.catSubIdx != null)
        binding.btnBackToParent.visibility = if (showBackToParent) View.VISIBLE else View.GONE
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
                applyHomeFeedUi()
            } else {
                // If we are inside a category, and data just arrived/updated, refresh sub-grid or listings
                val currentCat = cats.getOrNull(vm.catIdx - 1)
                if (currentCat != null) {
                    subCategoryAdapter.updateData(currentCat.subCategories)
                    buildSubTabs(currentCat)
                    if (isShowingSubGrid) {
                        applyBodyState(BodyState.SUBCATEGORIES)
                    } else {
                        binding.llFilterBar.visibility = View.VISIBLE
                        binding.llRegionRow.visibility = View.VISIBLE
                        updateChipStyles(vm.catType)
                        val listings = vm.listings.value ?: emptyList()
                        if (listings.isNotEmpty()) applyBodyState(BodyState.ADS)
                        else if (vm.isFirstPageLoading.value == true) applyBodyState(BodyState.LOADING)
                        else if (vm.isEmptyState.value == true) applyBodyState(BodyState.EMPTY)
                    }
                }
            }
        }

        vm.regions.observe(this) { regions -> buildRegionSpinner(regions) }
        vm.isHomeGridLoading.observe(this) { loading -> if (loading) applyBodyState(BodyState.GRID_LOADING) }

        vm.homeSubCategories.observe(this) { subs ->
            if (subs.isEmpty()) {
                if (vm.catIdx == 0) applyHomeFeedUi()
            } else {
                subCategoryAdapter.updateData(subs)
                applyBodyState(BodyState.SUBCATEGORIES)
            }
        }

        vm.isFirstPageLoading.observe(this) { loading ->
            if (loading && pendingCategoryId == null && !isShowingSubGrid && vm.catIdx >= 0) {
                applyBodyState(BodyState.LOADING)
            }
        }

        // No paging spinner under the list — pages append silently as you scroll.
        vm.listings.observe(this) { listings ->
            listingsAdapter.updateData(listings)
            // If a short page doesn't fill the screen the user can't scroll to
            // trigger the next one — keep filling until it does (or pages run out).
            binding.rvListings.post {
                if (!binding.rvListings.canScrollVertically(1)) maybeLoadNextPage()
            }
            if (!isShowingSubGrid && vm.catIdx == 0) {
                if (listings.isNotEmpty()) applyBodyState(BodyState.ADS)
                else applyBodyState(BodyState.EMPTY)
            } else if (!isShowingSubGrid && vm.catIdx > 0) {
                if (listings.isNotEmpty()) applyBodyState(BodyState.ADS)
                else applyBodyState(BodyState.EMPTY)
            }
        }

        vm.isEmptyState.observe(this) { empty ->
            if (empty && pendingCategoryId == null && !isShowingSubGrid && vm.catIdx >= 0) {
                applyBodyState(BodyState.EMPTY)
            }
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

        binding.btnAddAdEmpty.setOnClickListener {
            AuthGuard.requireLogin(this) {
                startActivity(Intent(this, AddAdActivity::class.java))
            }
        }

        binding.btnBackToParent.setOnClickListener {
            val cats = vm.categories.value ?: return@setOnClickListener
            val currentCat = cats.getOrNull(vm.catIdx - 1) ?: return@setOnClickListener
            openCategory(currentCat)
        }
    }

    private fun openCategory(cat: ApiCategory) {
        scrollContentToTop()
        val cats = vm.categories.value ?: return
        val pos = cats.indexOf(cat)
        vm.selectTopCategory(pos + 1)
        topTabAdapter.update(cats, pos + 1)
        
        isShowingSubGrid = true
        subCategoryAdapter.updateData(cat.subCategories)
        buildSubTabs(cat)
        applyBodyState(BodyState.SUBCATEGORIES)
    }

    fun resetToHome() {
        scrollContentToTop()
        val cats = vm.categories.value ?: emptyList()
        vm.selectTopCategory(0)
        topTabAdapter.update(cats, 0)
        isShowingSubGrid = false
        applyHomeFeedUi()
        applyBodyState(BodyState.LOADING)
        vm.fetchListings(reset = true)
    }

    /** Home tab: all ads feed (no category grid). */
    private fun applyHomeFeedUi() {
        binding.rvSubTabs.visibility   = View.GONE
        binding.rvExtraTabs.visibility = View.GONE
        binding.llFilterBar.visibility = View.VISIBLE
        binding.llRegionRow.visibility = View.VISIBLE
        updateChipStyles(vm.catType)
        if (vm.catRegId == null) {
            resetRegionPill()
            resetCityFilter()
        }
        val listings = vm.listings.value ?: emptyList()
        when {
            listings.isNotEmpty() -> applyBodyState(BodyState.ADS)
            vm.isEmptyState.value == true -> applyBodyState(BodyState.EMPTY)
            vm.isFirstPageLoading.value == true -> applyBodyState(BodyState.LOADING)
            else -> applyBodyState(BodyState.LOADING)
        }
    }

    private fun showListingsMode() {
        scrollContentToTop()
        isShowingSubGrid = false
        // Every row here (type chips, extras, region) already has a default value
        // selected — "All" — so show them all immediately instead of gating the
        // region row behind a redundant tap on a chip that's already active.
        binding.llFilterBar.visibility = View.VISIBLE
        binding.llRegionRow.visibility = View.VISIBLE
        updateChipStyles(vm.catType)
        if (vm.catRegId == null) {
            resetRegionPill()
            resetCityFilter()
        }
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
            binding.llRegionRow.visibility  = View.VISIBLE
            updateChipStyles(vm.catType)
            return
        }
        binding.rvSubTabs.visibility   = View.VISIBLE
        if (isShowingSubGrid) {
            binding.llFilterBar.visibility  = View.GONE   // hide chips while sub-grid is showing
            binding.llRegionRow.visibility  = View.GONE
        } else {
            binding.llFilterBar.visibility  = View.VISIBLE
            binding.llRegionRow.visibility  = View.VISIBLE
            updateChipStyles(vm.catType)
        }
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

        val currentRegId = vm.catRegId
        if (currentRegId != null) {
            val selectedRegion = regions.find { it.id == currentRegId }
            if (selectedRegion != null && !selectedRegion.isAllOption()) {
                setRegionPillActive(LocaleHelper.localizedName(this, selectedRegion.nameAr, selectedRegion.nameEn))
                buildCityDropdown(selectedRegion.id)
                val currentCityId = vm.catCityId
                if (currentCityId != null) {
                    val selectedCity = vm.citiesForRegion(selectedRegion.id).find { it.id == currentCityId }
                    if (selectedCity != null && !selectedCity.isAllOption()) {
                        setCityPillActive(LocaleHelper.localizedName(this, selectedCity.nameAr, selectedCity.nameEn))
                    }
                }
            } else {
                resetRegionPill()
                binding.spinnerCity.visibility = View.GONE
            }
        } else {
            resetRegionPill()
            binding.spinnerCity.visibility = View.GONE
        }

        binding.spinnerRegion.setOnClickListener {
            val popup = android.widget.PopupMenu(this, binding.spinnerRegion)
            regions.forEachIndexed { i, r ->
                popup.menu.add(0, i, i, LocaleHelper.localizedName(this, r.nameAr, r.nameEn))
            }
            popup.setOnMenuItemClickListener { item ->
                val region = regions[item.itemId]
                if (region.isAllOption()) {
                    resetRegionPill()
                    applyBodyState(BodyState.LOADING)
                    vm.selectRegion(null)
                    buildCityDropdown(null)
                } else {
                    setRegionPillActive(LocaleHelper.localizedName(this, region.nameAr, region.nameEn))
                    applyBodyState(BodyState.LOADING)
                    vm.selectRegion(region.id)
                    buildCityDropdown(region.id)
                }
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

        binding.spinnerCity.setOnClickListener {
            val popup = android.widget.PopupMenu(this, binding.spinnerCity)
            cities.forEachIndexed { i, c ->
                popup.menu.add(0, i, i, LocaleHelper.localizedName(this, c.nameAr, c.nameEn))
            }
            popup.setOnMenuItemClickListener { item ->
                val city = cities[item.itemId]
                if (city.isAllOption()) {
                    resetCityPill()
                    applyBodyState(BodyState.LOADING)
                    vm.selectCity(null)
                } else {
                    setCityPillActive(LocaleHelper.localizedName(this, city.nameAr, city.nameEn))
                    applyBodyState(BodyState.LOADING)
                    vm.selectCity(city)
                }
                true
            }
            popup.show()
        }
    }

    // ── Pill highlight helpers ────────────────────────────────────────────────

    private fun resetRegionPill() {
        binding.tvRegionLabel.text = getString(R.string.all_regions)
        binding.tvRegionLabel.setTextColor(getColor(R.color.tab_row_text))
        binding.spinnerRegion.setBackgroundResource(R.drawable.bg_spinner_pill)
        binding.ivRegionChevron.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tab_row_text))
    }

    private fun setRegionPillActive(label: String) {
        binding.tvRegionLabel.text = label
        binding.tvRegionLabel.setTextColor(getColor(R.color.tab_row_text))
        binding.spinnerRegion.setBackgroundResource(R.drawable.bg_spinner_pill)
        binding.ivRegionChevron.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tab_row_text))
    }

    private fun resetCityPill() {
        binding.tvCityLabel.text = getString(R.string.all_cities)
        binding.tvCityLabel.setTextColor(getColor(R.color.tab_row_text))
        binding.spinnerCity.setBackgroundResource(R.drawable.bg_spinner_pill)
        binding.ivCityChevron.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tab_row_text))
    }

    private fun setCityPillActive(label: String) {
        binding.tvCityLabel.text = label
        binding.tvCityLabel.setTextColor(getColor(R.color.tab_row_text))
        binding.spinnerCity.setBackgroundResource(R.drawable.bg_spinner_pill)
        binding.ivCityChevron.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tab_row_text))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_SHOWING_SUB_GRID, isShowingSubGrid)
    }

    companion object {
        const val EXTRA_CATEGORY_IDX = "extra_category_idx"
        private const val KEY_IS_SHOWING_SUB_GRID = "key_is_showing_sub_grid"
        /** Start loading the next page when this many rows remain below the viewport. */
        private const val PREFETCH_DISTANCE = 5
    }

    // One shared shimmer animator. Previously a new infinite animator was started
    // on every LOADING state and never cancelled, so they piled up and kept
    // running (off-screen too), stealing frames from scrolling.
    private var shimmerAnimator: android.animation.ObjectAnimator? = null

    private fun startShimmer() {
        if (shimmerAnimator?.isRunning == true) return
        shimmerAnimator = android.animation.ObjectAnimator
            .ofFloat(binding.llListingsShimmer, View.ALPHA, 0.4f, 1f, 0.4f).apply {
                duration = 1200
                repeatCount = android.animation.ValueAnimator.INFINITE
                start()
            }
    }

    private fun stopShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        binding.llListingsShimmer.alpha = 1f
    }

    override fun onDestroy() {
        stopShimmer()
        super.onDestroy()
    }

    private fun setupNavigation() {
        BottomNavHelper.setup(this, NavScreen.HOME)
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
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
        handleDeepLink(intent)
        handleIncomingCategoryIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val listingId = ListingDetailActivity.extractListingId(intent)
        if (!listingId.isNullOrBlank()) {
            intent.data = null
            intent.removeExtra(ListingDetailActivity.EXTRA_LISTING_ID)
            intent.removeExtra("id")
            val detailIntent = Intent(this, ListingDetailActivity::class.java).apply {
                putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listingId)
            }
            startWithPush(detailIntent)
        }
    }

    private fun handleNavigationIntent(intent: Intent) {
        val navScreenStr = intent.getStringExtra("EXTRA_NAV_SCREEN") ?: return
        intent.removeExtra("EXTRA_NAV_SCREEN")
        when (navScreenStr) {
            "ADD" -> {
                startActivity(Intent(this, AddAdActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            "CHAT" -> {
                startActivity(Intent(this, com.example.myapplication.chat.ui.conversations.ConversationsActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun loadFavoriteIds() {
        if (!TokenManager.isLoggedIn(this)) return
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.build(this@MainActivity)
                // /favorites is paginated as data.items + data.pagination — it is
                // NOT a bare data[] array. Reading it as an array returned null
                // and bailed out here, so no heart on the grid ever filled in.
                // Walk every page so users with many favorites are covered.
                val ids = withContext(Dispatchers.IO) {
                    val collected = mutableSetOf<String>()
                    var page = 1
                    var lastPage = 1
                    do {
                        val response = api.getFavorites(page = page)
                        if (!response.isSuccessful) break
                        val data = response.body()?.data ?: break
                        data.items.orEmpty().forEach { item ->
                            if (item.id.isNotEmpty()) collected.add(item.id)
                        }
                        lastPage = data.pagination?.lastPage ?: 1
                        page++
                    } while (page <= lastPage && page <= 20) // hard safety cap
                    collected
                }
                listingsAdapter.setFavoriteIds(ids)
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
