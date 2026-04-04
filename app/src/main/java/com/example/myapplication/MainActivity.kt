package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.adapters.MainContentAdapter
import com.example.myapplication.adapters.TopCategoryAdapter
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.models.Category

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var categoryAdapter: MainContentAdapter
    private lateinit var listingsAdapter: ListingsAdapter

    // Suppress spinner callbacks during programmatic updates
    private var suppressSpinner = false

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
        setupFilterBar()
        observeViewModel()
        setupNavigation()
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        categoryAdapter = MainContentAdapter(emptyList()) { item ->
            // Clicking any category/sub-category card drills down
            val level = viewModel.selectionStack.size
            viewModel.selectItemAtLevel(item, level)
        }
        listingsAdapter = ListingsAdapter(emptyList())
        binding.rvMainContent.layoutManager = GridLayoutManager(this, 3)
        binding.rvMainContent.adapter = categoryAdapter
    }

    // ── Filter bar ────────────────────────────────────────────────────────────

    private fun setupFilterBar() {
        // Type chips
        binding.chipAll.setOnClickListener { setTypeChip(null) }
        binding.chipOffer.setOnClickListener { setTypeChip("offer") }
        binding.chipRequest.setOnClickListener { setTypeChip("request") }

        // Region spinner
        binding.spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (suppressSpinner) return
                val regions = viewModel.regions.value ?: return
                val regionId = if (pos == 0) null else regions[pos - 1].id
                viewModel.catRegId = regionId
                viewModel.catCityId = null
                rebuildCitySpinner()
                if (viewModel.catIdx > 0) viewModel.fetchListings()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // City spinner
        binding.spinnerCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (suppressSpinner) return
                val regionId = viewModel.catRegId ?: return
                val cities = viewModel.cities.value?.filter { it.regionId == regionId } ?: return
                viewModel.catCityId = if (pos == 0) null else cities[pos - 1].id
                if (viewModel.catIdx > 0) viewModel.fetchListings()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setTypeChip(type: String?) {
        viewModel.catType = type
        val gold = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#C8A96E"))
        val gray = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EEEEEE"))
        binding.chipAll.backgroundTintList = if (type == null) gold else gray
        binding.chipAll.setTextColor(if (type == null) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#888888"))
        binding.chipOffer.backgroundTintList = if (type == "offer") gold else gray
        binding.chipOffer.setTextColor(if (type == "offer") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#888888"))
        binding.chipRequest.backgroundTintList = if (type == "request") gold else gray
        binding.chipRequest.setTextColor(if (type == "request") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#888888"))
        if (viewModel.catIdx > 0) viewModel.fetchListings()
    }

    private fun rebuildRegionSpinner(regions: List<RegionItem>) {
        suppressSpinner = true
        val names = listOf("كل المناطق") + regions.map { it.nameAr }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRegion.adapter = adapter
        val sel = regions.indexOfFirst { it.id == viewModel.catRegId }
        binding.spinnerRegion.setSelection(if (sel >= 0) sel + 1 else 0)
        suppressSpinner = false
        rebuildCitySpinner()
    }

    private fun rebuildCitySpinner() {
        val regionId = viewModel.catRegId
        val cities = viewModel.cities.value?.filter { it.regionId == regionId } ?: emptyList()
        if (regionId == null || cities.isEmpty()) {
            binding.spinnerCity.visibility = View.GONE
            return
        }
        suppressSpinner = true
        binding.spinnerCity.visibility = View.VISIBLE
        val names = listOf("كل المدن") + cities.map { it.nameAr }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCity.adapter = adapter
        val sel = cities.indexOfFirst { it.id == viewModel.catCityId }
        binding.spinnerCity.setSelection(if (sel >= 0) sel + 1 else 0)
        suppressSpinner = false
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.activeFilterLevels.observe(this) { levels ->
            rebuildFilterRows(levels, viewModel.selectionPath.value ?: emptyList())
        }
        viewModel.selectionPath.observe(this) { path ->
            rebuildFilterRows(viewModel.activeFilterLevels.value ?: emptyList(), path)
        }
        viewModel.mainContentItems.observe(this) { items ->
            if (items.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                categoryAdapter.updateData(items as List<Category>)
                switchToGrid()
            }
        }
        viewModel.listings.observe(this) { listings ->
            listingsAdapter.updateData(listings)
            if (listings.isNotEmpty()) switchToList()
        }
        viewModel.listingsLoading.observe(this) { loading ->
            binding.listingsProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.isEmptyState.observe(this) { empty ->
            binding.llEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        }
        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                binding.llFiltersContainer.removeAllViews()
                val shimmer = layoutInflater.inflate(R.layout.item_category_shimmer, binding.llFiltersContainer, false)
                binding.llFiltersContainer.addView(shimmer)
                android.animation.ObjectAnimator.ofFloat(shimmer, "alpha", 0.3f, 1f, 0.3f).apply {
                    duration = 1200; repeatCount = android.animation.ValueAnimator.INFINITE; start()
                }
            }
        }
        viewModel.isSubLoading.observe(this) { loading ->
            if (loading) {
                val shimmer = layoutInflater.inflate(R.layout.item_category_shimmer, binding.llFiltersContainer, false)
                shimmer.tag = "subShimmer"
                binding.llFiltersContainer.addView(shimmer)
                android.animation.ObjectAnimator.ofFloat(shimmer, "alpha", 0.3f, 1f, 0.3f).apply {
                    duration = 1200; repeatCount = android.animation.ValueAnimator.INFINITE; start()
                }
            } else {
                binding.llFiltersContainer.findViewWithTag<View>("subShimmer")
                    ?.let { binding.llFiltersContainer.removeView(it) }
            }
        }
        viewModel.regions.observe(this) { regions ->
            rebuildRegionSpinner(regions)
        }
        viewModel.apiErrorEvent.observe(this) { msg ->
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── View switching ────────────────────────────────────────────────────────

    private fun switchToGrid() {
        binding.hsvFilterBar.visibility = View.GONE
        binding.rvMainContent.layoutManager = GridLayoutManager(this, 3)
        binding.rvMainContent.adapter = categoryAdapter
        binding.rvMainContent.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE
    }

    private fun switchToList() {
        binding.hsvFilterBar.visibility = View.VISIBLE
        binding.rvMainContent.layoutManager = LinearLayoutManager(this)
        binding.rvMainContent.adapter = listingsAdapter
        binding.rvMainContent.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE
    }

    // ── Filter rows ───────────────────────────────────────────────────────────

    private fun rebuildFilterRows(levels: List<List<Category>>, path: List<Category>) {
        binding.llFiltersContainer.removeAllViews()
        levels.forEachIndexed { index, categories ->
            val rv = RecyclerView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.filter_row_height)
                )
                layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
                clipToPadding = false
                setPadding(resources.getDimensionPixelSize(R.dimen.filter_row_padding), 0,
                    resources.getDimensionPixelSize(R.dimen.filter_row_padding), 0)
                val selectedId = if (index < path.size) path[index].id else null
                adapter = TopCategoryAdapter(categories, selectedId) { item ->
                    viewModel.selectItemAtLevel(item, index)
                }
            }
            binding.llFiltersContainer.addView(rv)
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun setupNavigation() {
        binding.btnMenu.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, 0)
        }
        binding.navAdd.setOnClickListener { startActivity(Intent(this, AddAdActivity::class.java)) }
        binding.navChat.setOnClickListener {
            if (!TokenManager.isLoggedIn(this)) {
                startActivity(Intent(this, PhoneAuthActivity::class.java)); return@setOnClickListener
            }
            startActivity(Intent(this, ConversationsActivity::class.java))
        }
        binding.navHome.alpha = 1f
        binding.navChat.alpha = 0.45f
    }

    override fun onResume() {
        super.onResume()
        binding.navHome.alpha = 1f
        binding.navChat.alpha = 0.45f
    }
}
