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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var listingsAdapter: ListingsAdapter

    private var suppressSpinner = false

    // Gold accent color
    private val gold = Color.parseColor("#C8A96E")
    private val goldTint = Color.parseColor("#FFF9E6")
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

        setupListingsAdapter()
        setupTypeChips()
        observeViewModel()
        setupNavigation()
    }

    // ── Listings adapter ──────────────────────────────────────────────────────

    private fun setupListingsAdapter() {
        listingsAdapter = ListingsAdapter(emptyList())
        binding.rvContent.layoutManager = LinearLayoutManager(this)
        binding.rvContent.adapter = listingsAdapter
    }

    // ── Type chips ────────────────────────────────────────────────────────────

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

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.isBootLoading.observe(this) { loading ->
            binding.bootShimmer.visibility = if (loading) View.VISIBLE else View.GONE
            binding.hsvTopTabs.visibility = if (loading) View.GONE else View.VISIBLE
        }

        vm.categories.observe(this) { cats ->
            buildTopTabs(cats)
        }

        vm.regions.observe(this) { regions ->
            buildRegionSpinner(regions)
        }

        vm.isListingsLoading.observe(this) { loading ->
            binding.llListingsShimmer.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.rvContent.visibility = View.GONE
                binding.llEmptyState.visibility = View.GONE
                animateShimmer(binding.llListingsShimmer)
            }
        }

        vm.listings.observe(this) { listings ->
            if (listings.isNotEmpty()) {
                listingsAdapter.updateData(listings)
                binding.rvContent.visibility = View.VISIBLE
                binding.llListingsShimmer.visibility = View.GONE
                binding.llEmptyState.visibility = View.GONE
            }
        }

        vm.isEmptyState.observe(this) { empty ->
            if (empty) {
                binding.llEmptyState.visibility = View.VISIBLE
                binding.rvContent.visibility = View.GONE
                binding.llListingsShimmer.visibility = View.GONE
            }
        }

        vm.errorEvent.observe(this) { msg ->
            if (!msg.isNullOrEmpty()) {
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Top tabs ──────────────────────────────────────────────────────────────

    private fun buildTopTabs(cats: List<ApiCategory>) {
        val container = binding.llTopTabs
        container.removeAllViews()

        // "الرئيسية" home tab
        container.addView(makeTopTab("الرئيسية", 0, vm.catIdx == 0))

        cats.forEachIndexed { i, cat ->
            container.addView(makeTopTab(cat.nameAr, i + 1, vm.catIdx == i + 1))
        }
    }

    private fun makeTopTab(label: String, idx: Int, isActive: Boolean): View {
        val tv = TextView(this)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        tv.layoutParams = lp
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        tv.textSize = if (isActive) 18f else 15f
        tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(Color.BLACK)

        // Gold underline via compound drawable bottom trick — use a bottom border view instead
        // We wrap in a vertical LinearLayout to add the underline
        val wrapper = LinearLayout(this)
        val wlp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.layoutParams = wlp
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.gravity = Gravity.CENTER_HORIZONTAL

        val innerTv = TextView(this)
        val innerLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            0, 1f
        )
        innerTv.layoutParams = innerLp
        innerTv.text = label
        innerTv.gravity = Gravity.CENTER
        innerTv.setPadding(dp(14), 0, dp(14), 0)
        innerTv.textSize = if (isActive) 20f else 15f
        innerTv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        innerTv.setTextColor(Color.BLACK)

        val underline = View(this)
        val ulp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        underline.layoutParams = ulp
        underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        wrapper.addView(innerTv)
        wrapper.addView(underline)

        wrapper.setOnClickListener {
            vm.selectTopCategory(idx)
            buildTopTabs(vm.categories.value ?: emptyList())
            if (idx == 0) {
                // Home: hide sub/extra/filter rows, show category grid
                showHomeState()
            } else {
                val cat = vm.categories.value?.getOrNull(idx - 1) ?: return@setOnClickListener
                buildSubTabs(cat)
                showListingsState()
            }
        }
        return wrapper
    }

    // ── Sub-category tabs ─────────────────────────────────────────────────────

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

        // "الكل" chip — only add manually, never comes from API at this level
        container.addView(makeSubTab("الكل", null, vm.catSubIdx == null))
        subs.forEachIndexed { i, sub ->
            container.addView(makeSubTab(sub.nameAr, i, vm.catSubIdx == i))
        }

        // Build extra tabs for currently selected sub
        val selectedSub = if (vm.catSubIdx != null) subs.getOrNull(vm.catSubIdx!!) else null
        buildExtraTabs(selectedSub)

        binding.llFilterBar.visibility = View.VISIBLE
    }

    private fun makeSubTab(label: String, subIdx: Int?, isActive: Boolean): View {
        val wrapper = LinearLayout(this)
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.gravity = Gravity.CENTER_HORIZONTAL
        val wlp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.layoutParams = wlp

        val tv = TextView(this)
        val tvlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f)
        tv.layoutParams = tvlp
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        tv.textSize = 14f
        tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(if (isActive) Color.BLACK else gray)

        val underline = View(this)
        val ulp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        underline.layoutParams = ulp
        underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        wrapper.addView(tv)
        wrapper.addView(underline)

        wrapper.setOnClickListener {
            vm.selectSubCategory(subIdx)
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1) ?: return@setOnClickListener
            buildSubTabs(cat)
        }
        return wrapper
    }

    // ── Extra (filter_option) tabs ────────────────────────────────────────────

    private fun buildExtraTabs(sub: ApiSubCategory?) {
        val extras = sub?.filterOptions ?: emptyList()
        if (extras.isEmpty()) {
            binding.hsvExtraTabs.visibility = View.GONE
            return
        }
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
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.gravity = Gravity.CENTER_HORIZONTAL
        val wlp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.layoutParams = wlp

        val tv = TextView(this)
        val tvlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f)
        tv.layoutParams = tvlp
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        tv.textSize = 13f
        tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(if (isActive) Color.BLACK else gray)

        val underline = View(this)
        val ulp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        underline.layoutParams = ulp
        underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        wrapper.addView(tv)
        wrapper.addView(underline)

        wrapper.setOnClickListener {
            vm.selectExtra(extraIdx)
            val cat = vm.categories.value?.getOrNull(vm.catIdx - 1) ?: return@setOnClickListener
            val sub = if (vm.catSubIdx != null) cat.subCategories.getOrNull(vm.catSubIdx!!) else null
            buildExtraTabs(sub)
        }
        return wrapper
    }

    // ── Region / City spinners ────────────────────────────────────────────────

    // Track last built list to avoid rebuilding when nothing changed
    private var lastRegionList: List<RegionItem> = emptyList()

    private fun buildRegionSpinner(regions: List<RegionItem>) {
        if (regions == lastRegionList && binding.spinnerRegion.adapter != null) return
        lastRegionList = regions

        suppressSpinner = true
        val names = regions.map { it.nameAr }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
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
        if (regionId == null || cities.isEmpty()) {
            binding.spinnerCity.visibility = View.GONE
            return
        }
        suppressSpinner = true
        binding.spinnerCity.visibility = View.VISIBLE
        val names = cities.map { it.nameAr }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
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

    // ── View state helpers ────────────────────────────────────────────────────

    private fun showHomeState() {
        binding.hsvSubTabs.visibility = View.GONE
        binding.hsvExtraTabs.visibility = View.GONE
        binding.llFilterBar.visibility = View.GONE
        binding.rvContent.visibility = View.GONE
        binding.llListingsShimmer.visibility = View.GONE
        binding.llEmptyState.visibility = View.GONE
    }

    private fun showListingsState() {
        // listings/shimmer/empty visibility is managed by observers
    }

    // ── Shimmer animation ─────────────────────────────────────────────────────

    private fun animateShimmer(view: View) {
        android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.4f, 1f, 0.4f).apply {
            duration = 1200
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
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
                startActivity(Intent(this, PhoneAuthActivity::class.java))
                return@setOnClickListener
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

    // ── Util ──────────────────────────────────────────────────────────────────

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
