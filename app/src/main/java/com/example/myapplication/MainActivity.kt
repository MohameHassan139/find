package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.myapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

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

    private val gray = "#888888".toColorInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupAdapters()
        setupTypeChips()
        observeViewModel()
        setupNavigation()
        setupSearch()
        refreshUserProfile()
    }

    private fun setupSearch() {
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false
        binding.llSearchContainer.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
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

        listingsAdapter = ListingsAdapter(emptyList()) { _ ->
            // Detail
        }
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
                binding.rvSubTabs.visibility   = View.GONE
                binding.rvExtraTabs.visibility = View.GONE
                binding.llFilterBar.visibility  = View.GONE
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
        binding.chipAll.setOnClickListener { vm.selectType(null); updateChipStyles(null) }
        binding.chipOffer.setOnClickListener { vm.selectType("offer"); updateChipStyles("offer") }
        binding.chipRequest.setOnClickListener { vm.selectType("request"); updateChipStyles("request") }
    }

    private fun updateChipStyles(active: String?) {
        fun style(tv: TextView, isActive: Boolean) {
            tv.setBackgroundResource(if (isActive) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            tv.setTextColor(if (isActive) Color.BLACK else gray)
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
            binding.rvSubTabs.visibility   = View.GONE
            binding.rvExtraTabs.visibility = View.GONE
            binding.llFilterBar.visibility  = View.GONE
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
        applyBodyState(BodyState.LOADING)
        vm.fetchListings()
    }

    private fun buildSubTabs(cat: ApiCategory) {
        val subs = cat.subCategories
        if (subs.isEmpty()) {
            binding.rvSubTabs.visibility = View.GONE
            binding.rvExtraTabs.visibility = View.GONE
            binding.llFilterBar.visibility = View.VISIBLE
            return
        }
        binding.rvSubTabs.visibility = View.VISIBLE
        subTabAdapter.update(subs, vm.catSubIdx)
        
        val selectedSub = vm.catSubIdx?.let { subs.getOrNull(it) }
        buildExtraTabs(selectedSub)
        binding.llFilterBar.visibility = View.VISIBLE
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
        if (regions == lastRegionList && binding.spinnerRegion.adapter != null) return
        lastRegionList = regions
        suppressSpinner = true
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, regions.map { it.nameAr })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRegion.adapter = adapter
        binding.spinnerRegion.setSelection(0)
        suppressSpinner = false
        binding.spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (suppressSpinner) return
                val regionId = regions[pos].id
                vm.selectRegion(regionId)
                buildCitySpinner(regionId)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        binding.spinnerCity.visibility = View.GONE
    }

    private fun buildCitySpinner(regionId: Int?) {
        val cities = vm.citiesForRegion(regionId)
        if (regionId == null || cities.isEmpty()) { binding.spinnerCity.visibility = View.GONE; return }
        suppressSpinner = true
        binding.spinnerCity.visibility = View.VISIBLE
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cities.map { it.nameAr })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCity.adapter = adapter
        binding.spinnerCity.setSelection(0)
        suppressSpinner = false
        binding.spinnerCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (suppressSpinner) return
                vm.selectCity(cities[pos].id)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private var pendingCategoryId: Int? = null
    private var isShowingSubGrid = false

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
    }
}
