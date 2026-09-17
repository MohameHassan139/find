package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.myapplication.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.databinding.ActivityCategorySelectionBinding
import com.example.myapplication.utils.CategoryIconHelper
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper

class CategorySelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityCategorySelectionBinding
    private lateinit var vm: MainViewModel

    private enum class State { MAIN, SUB, FILTER, TYPE }
    private var currentState = State.MAIN

    private var selectedMain: ApiCategory? = null
    private var selectedSub: ApiSubCategory? = null
    private var selectedFilter: ApiFilterOption? = null
    private var selectedType = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCategorySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        vm = ViewModelProvider(this)[MainViewModel::class.java]

        HomeHeaderHelper.attach(this, binding.root, vm.categories)

        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { onBackPressed() }
        BottomNavHelper.setup(this, NavScreen.NONE)

        // Step 1: Main Category
        showMainCategories(vm.categories.value ?: emptyList())

        // Load categories if not already loaded
        vm.categories.observe(this) { cats ->
            if (currentState == State.MAIN) showMainCategories(cats)
        }
    }

    // ── Step 1: Main categories from API ────────────────────────────────────

    private fun showMainCategories(cats: List<ApiCategory>) {
        currentState = State.MAIN
        selectedMain = null
        selectedSub = null
        selectedFilter = null
        binding.llListContainer.removeAllViews()

        if (cats.isEmpty()) {
            val tv = TextView(this).apply {
                text = LocaleHelper.localizedName(this@CategorySelectionActivity, "جارٍ التحميل...", "Loading...")
                setPadding(32, 32, 32, 32)
            }
            binding.llListContainer.addView(tv)
            return
        }

        for (cat in cats) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            item.findViewById<View>(R.id.flIconHolder)?.visibility = View.GONE
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, cat.nameAr, cat.nameEn)
            item.setOnClickListener {
                selectedMain = cat
                showSubCategories(cat)
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Step 2: Sub-categories from API ─────────────────────────────────────

    private fun showSubCategories(cat: ApiCategory) {
        currentState = State.SUB
        selectedSub = null
        selectedFilter = null
        binding.llListContainer.removeAllViews()

        val realSubs = cat.subCategories.filter { !it.isAllOption() }

        if (realSubs.isEmpty()) {
            showTypes()
            return
        }

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        for (sub in realSubs) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            val flHolder = item.findViewById<FrameLayout>(R.id.flIconHolder)
            val ivIcon = item.findViewById<ImageView>(R.id.ivItemIcon)
            val wvIcon = item.findViewById<android.webkit.WebView>(R.id.wvItemIcon)

            if (flHolder != null && ivIcon != null) {
                flHolder.visibility = View.VISIBLE
                wvIcon?.visibility = View.GONE
                ivIcon.visibility = View.VISIBLE
                val url = sub.iconUrl(isDark) ?: sub.iconName
                CategoryIconHelper.loadSvg(ivIcon, url, isDark, R.drawable.ic_category_placeholder)
            }

            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, sub.nameAr, sub.nameEn)
            item.setOnClickListener {
                selectedSub = sub
                val realFilters = sub.filterOptions.filter { !it.isAllOption() }
                if (realFilters.isNotEmpty()) {
                    showFilterOptions(cat, sub)
                } else {
                    showTypes()
                }
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Step 3: Filter options (e.g. car brands) ─────────────────────────────

    private fun showFilterOptions(cat: ApiCategory, sub: ApiSubCategory) {
        currentState = State.FILTER
        selectedFilter = null
        binding.llListContainer.removeAllViews()

        val realFilters = sub.filterOptions.filter { !it.isAllOption() }
        if (realFilters.isEmpty()) {
            showTypes()
            return
        }

        for (opt in realFilters) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            item.findViewById<View>(R.id.flIconHolder)?.visibility = View.GONE
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, opt.nameAr, opt.nameEn)
            item.setOnClickListener {
                selectedFilter = opt
                showTypes()
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Step 4: Offer / Request (FINAL STEP matching iOS) ───────────────────

    private fun showTypes() {
        currentState = State.TYPE
        binding.llListContainer.removeAllViews()

        val types = listOf(
            LocaleHelper.localizedName(this, "عرض", "Offer") to "offer",
            LocaleHelper.localizedName(this, "طلب", "Request") to "request"
        )

        for ((label, value) in types) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            item.findViewById<View>(R.id.flIconHolder)?.visibility = View.GONE
            item.findViewById<TextView>(R.id.tvItemName).text = label
            item.setOnClickListener {
                selectedType = value
                selectedMain?.let { mainCat ->
                    returnResult(mainCat, selectedSub, selectedFilter)
                }
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Return result ────────────────────────────────────────────────────────

    private fun returnResult(cat: ApiCategory, sub: ApiSubCategory?, filter: ApiFilterOption?) {
        val catLabel = LocaleHelper.localizedName(this, cat.nameAr, cat.nameEn)
        val subLabel = sub?.let { LocaleHelper.localizedName(this, it.nameAr, it.nameEn) }
        val filterLabel = filter?.let { LocaleHelper.localizedName(this, it.nameAr, it.nameEn) }
        val typeLabel = if (selectedType == "offer")
            LocaleHelper.localizedName(this, "عرض", "Offer")
        else
            LocaleHelper.localizedName(this, "طلب", "Request")

        val displayText = listOfNotNull(catLabel, subLabel, filterLabel, typeLabel).joinToString(" / ")

        val resultIntent = Intent()
        resultIntent.putExtra("selected_category", displayText)
        resultIntent.putExtra("selected_type", selectedType)
        resultIntent.putExtra("selected_category_id", cat.id)
        resultIntent.putExtra("selected_sub_category_id", sub?.id ?: -1)
        resultIntent.putExtra("selected_filter_option_id", filter?.id ?: -1)
        setResult(Activity.RESULT_OK, resultIntent)
        finishWithPop()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (currentState) {
            State.TYPE -> {
                val sub = selectedSub
                if (sub != null && sub.filterOptions.any { !it.isAllOption() }) {
                    selectedMain?.let { showFilterOptions(it, sub) }
                } else if (selectedMain != null) {
                    showSubCategories(selectedMain!!)
                } else {
                    showMainCategories(vm.categories.value ?: emptyList())
                }
            }
            State.FILTER -> selectedMain?.let { showSubCategories(it) }
            State.SUB -> showMainCategories(vm.categories.value ?: emptyList())
            State.MAIN -> finishWithPop()
        }
    }
}
