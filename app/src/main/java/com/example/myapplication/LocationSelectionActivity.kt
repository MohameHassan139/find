package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.databinding.ActivityLocationSelectionBinding
import com.example.myapplication.utils.LocaleHelper

class LocationSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityLocationSelectionBinding
    private lateinit var vm: MainViewModel

    private var selectedRegion: RegionItem? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLocationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        vm = ViewModelProvider(this)[MainViewModel::class.java]

        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        BottomNavHelper.setup(this, NavScreen.NONE)

        vm.regions.observe(this) { regions ->
            if (selectedRegion == null) showRegions(regions)
        }
    }

    // ── Regions ──────────────────────────────────────────────────────────────

    private fun showRegions(regions: List<RegionItem>) {
        selectedRegion = null
        binding.llListContainer.removeAllViews()

        if (regions.isEmpty()) {
            val tv = TextView(this).apply {
                text = LocaleHelper.localizedName(this@LocationSelectionActivity, "جارٍ التحميل...", "Loading...")
                setPadding(32, 32, 32, 32)
            }
            binding.llListContainer.addView(tv)
            return
        }

        for (region in regions) {
            val item = layoutInflater.inflate(R.layout.item_location, binding.llListContainer, false)
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, region.nameAr, region.nameEn)
            item.setOnClickListener {
                selectedRegion = region
                showCities(region)
            }
            binding.llListContainer.addView(item)
        }
    }

    // ── Cities ───────────────────────────────────────────────────────────────

    private fun showCities(region: RegionItem) {
        binding.llListContainer.removeAllViews()
        val cities = vm.citiesForRegion(region.id)

        if (cities.isEmpty()) {
            val tv = TextView(this).apply {
                text = LocaleHelper.localizedName(this@LocationSelectionActivity, "لا توجد مدن", "No cities")
                setPadding(32, 32, 32, 32)
            }
            binding.llListContainer.addView(tv)
            return
        }

        for (city in cities) {
            val item = layoutInflater.inflate(R.layout.item_location, binding.llListContainer, false)
            item.findViewById<TextView>(R.id.tvItemName).text =
                LocaleHelper.localizedName(this, city.nameAr, city.nameEn)
            item.setOnClickListener {
                val resultIntent = Intent()
                val regionLabel = LocaleHelper.localizedName(this, region.nameAr, region.nameEn)
                val cityLabel = LocaleHelper.localizedName(this, city.nameAr, city.nameEn)
                resultIntent.putExtra("selected_location", "$regionLabel - $cityLabel")
                resultIntent.putExtra("selected_region_id", region.id)
                resultIntent.putExtra("selected_city_id", city.id)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            binding.llListContainer.addView(item)
        }
    }

    override fun onBackPressed() {
        if (selectedRegion != null) {
            showRegions(vm.regions.value ?: emptyList())
        } else {
            super.onBackPressed()
        }
    }
}
