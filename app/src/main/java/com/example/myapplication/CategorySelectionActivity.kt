package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.databinding.ActivityCategorySelectionBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper

class CategorySelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityCategorySelectionBinding
    private lateinit var vm: MainViewModel

    private enum class State { TYPE, MAIN, SUB, FILTER }
    private var currentState = State.TYPE

    private var selectedType = ""
    private var selectedMain: ApiCategory? = null
    private var selectedSub: ApiSubCategory? = null

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
            startActivity(Intent(this, MenuActivity::class.java))
        }
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        BottomNavHelper.setup(this, NavScreen.NONE)

        showTypes()

        // Load categories if not already loaded
        vm.categories.observe(this) { cats ->
            if (currentState == State.MAIN) showMainCategories(cats)
        }
    }

    // ── Step 1: Offer / Request ──────────────────────────────────────────────

    private fun showTypes() {
        currentState = State.TYPE
        binding.llListContainer.removeAllViews()

        val types = listOf(
            LocaleHelper.localizedName(this, "عرض", "Offer") to "offer",
            LocaleHelper.localizedName(this, "طلب", "Request") to "request"
        )

        for ((label, value) in types) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            item.findViewById<TextView>(R.id.tvItemName).text = label
            item.setOnClickListener {
                selectedType = value
                showMainCategories(vm.categories.value ?: emptyList())
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Step 2: Main categories from API ────────────────────────────────────

    private fun showMainCategories(cats: List<ApiCategory>) {
        currentState = State.MAIN
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
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, cat.nameAr, cat.nameEn)
            item.setOnClickListener {
                selectedMain = cat
                showSubCategories(cat)
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Step 3: Sub-categories from API ─────────────────────────────────────

    private fun showSubCategories(cat: ApiCategory) {
        currentState = State.SUB
        binding.llListContainer.removeAllViews()

        if (cat.subCategories.isEmpty()) {
            returnResult(cat, null, null)
            return
        }

        for (sub in cat.subCategories) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, sub.nameAr, sub.nameEn)
            item.setOnClickListener {
                selectedSub = sub
                if (sub.filterOptions.isNotEmpty()) {
                    showFilterOptions(cat, sub)
                } else {
                    returnResult(cat, sub, null)
                }
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Step 4: Filter options (e.g. car brands) ─────────────────────────────

    private fun showFilterOptions(cat: ApiCategory, sub: ApiSubCategory) {
        currentState = State.FILTER
        binding.llListContainer.removeAllViews()

        for (opt in sub.filterOptions) {
            val item = layoutInflater.inflate(R.layout.item_category, binding.llListContainer, false)
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, opt.nameAr, opt.nameEn)
            item.setOnClickListener {
                returnResult(cat, sub, opt)
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

        val displayText = listOfNotNull(typeLabel, catLabel, subLabel, filterLabel).joinToString(" - ")

        val resultIntent = Intent()
        resultIntent.putExtra("selected_category", displayText)
        resultIntent.putExtra("selected_type", selectedType)
        resultIntent.putExtra("selected_category_id", cat.id)
        resultIntent.putExtra("selected_sub_category_id", sub?.id ?: -1)
        resultIntent.putExtra("selected_filter_option_id", filter?.id ?: -1)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        when (currentState) {
            State.FILTER -> selectedMain?.let { showSubCategories(it) }
            State.SUB -> showMainCategories(vm.categories.value ?: emptyList())
            State.MAIN -> showTypes()
            State.TYPE -> super.onBackPressed()
        }
    }
}
