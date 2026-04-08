package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapters.CategoryGridAdapter
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.adapters.SubCategoryGridAdapter
import com.example.myapplication.auth.AuthRetrofitClient
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    private lateinit var categoryAdapter: CategoryGridAdapter
    private lateinit var subCategoryAdapter: SubCategoryGridAdapter
    private lateinit var listingsAdapter: ListingsAdapter

    private var suppressSpinner = false
    private var lastRegionList: List<RegionItem> = emptyList()

    private val gold = Color.parseColor("#C8A96E")
    private val gray = Color.parseColor("#888888")

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
        binding.etSearch.setOnClickListener {
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
            } catch (_: Exception) {}
        }
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        categoryAdapter = CategoryGridAdapter(emptyList()) { cat ->
            openCategory(cat)
        }
        binding.rvCategoryGrid.layoutManager = GridLayoutManager(this, 3)
        binding.rvCategoryGrid.adapter = categoryAdapter

        subCategoryAdapter = SubCategoryGridAdapter(emptyList()) { sub ->
            val cats = vm.categories.value ?: return@SubCategoryGridAdapter
            val parentCat = cats.firstOrNull { c -> c.subCategories.any { it.id == sub?.id } }
                ?: cats.getOrNull(vm.catIdx - 1)
                ?: return@SubCategoryGridAdapter

            if (sub == null) {
                vm.setCategoryIndex(cats.indexOfFirst { it.id == parentCat.id } + 1)
                vm.selectSubCategory(null)
                buildTopTabs(cats)
                buildSubTabs(parentCat)
                showListingsMode()
            } else {
                val catIdx = cats.indexOfFirst { it.id == parentCat.id } + 1
                val subIdx = parentCat.subCategories.indexOfFirst { it.id == sub.id }
                vm.setCategoryIndex(catIdx)
                vm.selectSubCategory(subIdx)
                buildTopTabs(cats)
                buildSubTabs(parentCat)
                showListingsMode()
            }
        }
        binding.rvSubCategoryGrid.layoutManager = GridLayoutManager(this, 3)
        binding.rvSubCategoryGrid.adapter = subCategoryAdapter

        listingsAdapter = ListingsAdapter(emptyList()) { listing ->
            val intent = Intent(this, ListingDetailActivity::class.java)
            intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listing.id)
            startActivity(intent)
        }
        val llm = LinearLayoutManager(this)
        binding.rvListings.layoutManager = llm
        binding.rvListings.adapter = listingsAdapter

        binding.rvListings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val total = llm.itemCount
                val lastVisible = llm.findLastVisibleItemPosition()
                if (lastVisible >= total - 3 && vm.hasMorePages()) vm.loadNextPage()
            }
        })
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
            binding.hsvTopTabs.visibility  = if (loading) View.GONE   else View.VISIBLE
        }

        vm.categories.observe(this) { cats ->
            buildTopTabs(cats)
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
            }
        }

        vm.regions.observe(this) { regions -> buildRegionSpinner(regions) }

        vm.isHomeGridLoading.observe(this) { loading ->
            if (loading) applyBodyState(BodyState.GRID_LOADING)
        }

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
            if (loading && pendingCategoryId == null && !isShowingSubGrid) {
                applyBodyState(BodyState.LOADING)
            }
        }

        vm.isPagingLoading.observe(this) { loading ->
            listingsAdapter.setFooterLoading(loading)
        }

        vm.listings.observe(this) { listings ->
            listingsAdapter.updateData(listings)
            if (listings.isNotEmpty() && !isShowingSubGrid) {
                applyBodyState(BodyState.ADS)
            }
        }

        vm.isEmptyState.observe(this) { empty ->
            if (empty && pendingCategoryId == null && !isShowingSubGrid) {
                applyBodyState(BodyState.EMPTY)
            }
        }

        vm.errorEvent.observe(this) { msg ->
            if (!msg.isNullOrEmpty())
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnResetFilters.setOnClickListener {
            vm.selectTopCategory(0)
            updateChipStyles(null)
            buildTopTabs(vm.categories.value ?: emptyList())
            categoryAdapter.updateData(vm.categories.value ?: emptyList())
            binding.hsvSubTabs.visibility   = View.GONE
            binding.hsvExtraTabs.visibility = View.GONE
            binding.llFilterBar.visibility  = View.GONE
            applyBodyState(BodyState.CATEGORIES)
        }
    }

    private fun buildTopTabs(cats: List<ApiCategory>) {
        val container = binding.llTopTabs
        container.removeAllViews()
        container.addView(makeTopTab("الرئيسية", 0, vm.catIdx == 0))
        cats.forEachIndexed { i, cat ->
            container.addView(makeTopTab(cat.nameAr, i + 1, vm.catIdx == i + 1))
        }
    }

    private fun makeTopTab(label: String, idx: Int, isActive: Boolean): View {
        val wrapper = LinearLayout(this)
        wrapper.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.gravity = Gravity.CENTER_HORIZONTAL

        val tv = TextView(this)
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f
        )
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        tv.textSize = if (isActive) 20f else 15f
        tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(Color.BLACK)

        val underline = View(this)
        underline.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        )
        underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        wrapper.addView(tv)
        wrapper.addView(underline)

        wrapper.setOnClickListener {
            val cats = vm.categories.value ?: return@setOnClickListener
            if (idx == 0) {
                vm.selectTopCategory(0)
                buildTopTabs(cats)
                categoryAdapter.updateData(cats)
                binding.hsvSubTabs.visibility   = View.GONE
                binding.hsvExtraTabs.visibility = View.GONE
                binding.llFilterBar.visibility  = View.GONE
                applyBodyState(BodyState.CATEGORIES)
            } else {
                val cat = cats.getOrNull(idx - 1) ?: return@setOnClickListener
                openCategory(cat)
            }
        }
        return wrapper
    }

    private fun buildSubTabs(cat: ApiCategory) {
        val subs = cat.subCategories
        if (subs.isEmpty()) {
            binding.hsvSubTabs.visibility = View.GONE
            binding.hsvExtraTabs.visibility = View.GONE
            binding.llFilterBar.visibility = View.VISIBLE
            return
        }
        binding.hsvSubTabs.visibility = View.VISIBLE
        val container = binding.llSubTabs
        container.removeAllViews()
        container.addView(makeSubTab("الكل", null, vm.catSubIdx == null))
        subs.forEachIndexed { i, sub ->
            container.addView(makeSubTab(sub.nameAr, i, vm.catSubIdx == i))
        }
        val selectedSub = vm.catSubIdx?.let { subs.getOrNull(it) }
        buildExtraTabs(selectedSub)
        binding.llFilterBar.visibility = View.VISIBLE
    }

    private fun makeSubTab(label: String, subIdx: Int?, isActive: Boolean): View {
        val wrapper = LinearLayout(this)
        wrapper.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.gravity = Gravity.CENTER_HORIZONTAL

        val tv = TextView(this)
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f
        )
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        tv.textSize = 14f
        tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(if (isActive) Color.BLACK else gray)

        val underline = View(this)
        underline.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        )
        underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        wrapper.addView(tv)
        wrapper.addView(underline)

        wrapper.setOnClickListener {
            vm.selectSubCategory(subIdx)
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1) ?: return@setOnClickListener
            buildSubTabs(cat)
            showListingsMode()
        }
        return wrapper
    }

    private fun buildExtraTabs(sub: ApiSubCategory?) {
        val extras = sub?.filterOptions ?: emptyList()
        if (extras.isEmpty()) { binding.hsvExtraTabs.visibility = View.GONE; return }
        binding.hsvExtraTabs.visibility = View.VISIBLE
        val container = binding.llExtraTabs
        container.removeAllViews()
        container.addView(makeExtraTab("الكل", null, vm.catExtraIdx == null))
        extras.forEachIndexed { i, opt ->
            container.addView(makeExtraTab(opt.nameAr, i, vm.catExtraIdx == i))
        }
    }

    private fun makeExtraTab(label: String, extraIdx: Int?, isActive: Boolean): View {
        val wrapper = LinearLayout(this)
        wrapper.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.gravity = Gravity.CENTER_HORIZONTAL

        val tv = TextView(this)
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f
        )
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        tv.textSize = 13f
        tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(if (isActive) Color.BLACK else gray)

        val underline = View(this)
        underline.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        )
        underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        wrapper.addView(tv)
        wrapper.addView(underline)

        wrapper.setOnClickListener {
            vm.selectExtra(extraIdx)
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1) ?: return@setOnClickListener
            val sub = vm.catSubIdx?.let { cat.subCategories.getOrNull(it) }
            buildExtraTabs(sub)
            showListingsMode()
        }
        return wrapper
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

    private fun showListingsMode() {
        isShowingSubGrid = false
        binding.rvCategoryGrid.visibility    = View.GONE
        binding.rvSubCategoryGrid.visibility = View.GONE
        binding.pbHomeGrid.visibility        = View.GONE
    }

    private var pendingCategoryId: Int? = null
    private var isShowingSubGrid = false

    private fun openCategory(cat: ApiCategory) {
        val cats = vm.categories.value ?: return
        val idx = cats.indexOfFirst { it.id == cat.id } + 1
        if (idx <= 0) return

        vm.setCategoryIndex(idx)
        buildTopTabs(cats)

        val freshCat = cats.find { it.id == cat.id } ?: cat
        val subs = freshCat.subCategories

        if (subs.isNotEmpty()) {
            pendingCategoryId = null
            isShowingSubGrid = true
            subCategoryAdapter.updateData(subs)
            buildSubTabs(freshCat)
            applyBodyState(BodyState.SUBCATEGORIES)
        } else {
            pendingCategoryId = cat.id
            isShowingSubGrid = true
            applyBodyState(BodyState.GRID_LOADING)
            vm.fetchCategoryDetails(cat.id)
        }
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

        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.slide_in_left, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        BottomNavHelper.setup(this, NavScreen.HOME)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
