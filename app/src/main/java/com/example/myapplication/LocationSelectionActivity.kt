package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityLocationSelectionBinding

class LocationSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationSelectionBinding
    private var selectedRegion: String? = null

    private val regions = listOf(
        "منطقة مكة المكرمة",
        "منطقة المدينة المنورة",
        "منطقة القصيم",
        "المنطقة الشرقية",
        "منطقة عسير",
        "منطقة تبوك",
        "منطقة حائل",
        "منطقة الحدود الشمالية",
        "منطقة جازان"
    )

    private val citiesMap = mapOf(
        "منطقة تبوك" to listOf("تبوك", "ضباء", "الوجه", "أملج", "حقل", "تيماء", "البدع"),
        "منطقة مكة المكرمة" to listOf("مكة المكرمة", "جدة", "الطائف", "القنفذة"),
        "منطقة المدينة المنورة" to listOf("المدينة المنورة", "ينبع", "العلا"),
        "المنطقة الشرقية" to listOf("الدمام", "الخبر", "الظهران", "الأحساء", "الجبيل")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMenu.setOnClickListener { finish() }

        showRegions()
    }

    private fun showRegions() {
        selectedRegion = null
        binding.llListContainer.removeAllViews()
        for (region in regions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_location, binding.llListContainer, false)
            itemView.findViewById<TextView>(R.id.tvItemName).text = region
            itemView.setOnClickListener {
                selectedRegion = region
                showCities(region)
            }
            binding.llListContainer.addView(itemView)
        }
    }

    private fun showCities(region: String) {
        binding.llListContainer.removeAllViews()
        val cities = citiesMap[region] ?: listOf("مدينة ١", "مدينة ٢", "مدينة ٣") // Fallback
        for (city in cities) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_location, binding.llListContainer, false)
            itemView.findViewById<TextView>(R.id.tvItemName).text = city
            itemView.setOnClickListener {
                val resultIntent = Intent()
                resultIntent.putExtra("selected_location", "$region $city")
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            binding.llListContainer.addView(itemView)
        }
    }

    override fun onBackPressed() {
        if (selectedRegion != null) {
            showRegions() // Go back to regions list
        } else {
            super.onBackPressed() // Exit activity
        }
    }
}
