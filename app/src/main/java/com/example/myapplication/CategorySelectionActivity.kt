package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityCategorySelectionBinding

class CategorySelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategorySelectionBinding

    private enum class CategoryState { TYPE, MAIN, SUB, OFFER_TYPE }
    private var currentState = CategoryState.TYPE

    private var selectedType = ""
    private var selectedMain = ""
    private var selectedSub = ""

    private val types = listOf("عرض", "طلب")
    private val mainCategories = listOf(
        "عقارات",
        "مركبات ولوازمها",
        "أجهزة إلكترونية",
        "أجهزة منزلية",
        "أجهزة رياضية",
        "أثاث",
        "لوازم رحلات",
        "حيوانات ولوازمها",
        "نباتات",
        "أخرى"
    )

    private val subVehicles = listOf(
        "سيارات", "دراجات نارية", "دراجات هوائية", "شاحنات", "شاحنات خلاط", "سطحات", "معدات ثقيلة", "كرفانات", "سفن", "جت بوت"
    )
    
    private val subRealEstate = listOf(
        "شقق", "فلل", "أراضي", "عمائر", "محلات", "مكاتب", "مزارع", "استراحات", "شاليهات"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategorySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMenu.setOnClickListener { finish() }

        showTypes()
    }

    private fun showTypes() {
        currentState = CategoryState.TYPE
        binding.llListContainer.removeAllViews()
        for (type in types) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_category, binding.llListContainer, false)
            itemView.findViewById<TextView>(R.id.tvItemName).text = type
            itemView.setOnClickListener {
                selectedType = type
                showMainCategories()
            }
            binding.llListContainer.addView(itemView)
        }
    }

    private fun showMainCategories() {
        currentState = CategoryState.MAIN
        binding.llListContainer.removeAllViews()
        for (main in mainCategories) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_category, binding.llListContainer, false)
            itemView.findViewById<TextView>(R.id.tvItemName).text = main
            itemView.setOnClickListener {
                selectedMain = main
                if (main == "عقارات") {
                    showSubCategories(subRealEstate, showIcons = false)
                } else if (main == "مركبات ولوازمها") {
                    showSubCategories(subVehicles, showIcons = true)
                } else {
                    showSubCategories(listOf("أخرى ١", "أخرى ٢"), showIcons = false)
                }
            }
            binding.llListContainer.addView(itemView)
        }
    }

    private fun showSubCategories(subs: List<String>, showIcons: Boolean) {
        currentState = CategoryState.SUB
        binding.llListContainer.removeAllViews()
        for (sub in subs) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_category, binding.llListContainer, false)
            itemView.findViewById<TextView>(R.id.tvItemName).text = sub
            
            if (showIcons) {
                val iconView = itemView.findViewById<ImageView>(R.id.ivItemIcon)
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(R.drawable.ic_vehicle_placeholder)
            }

            itemView.setOnClickListener {
                selectedSub = sub
                if (selectedMain == "عقارات") {
                    showRealEstateOfferType()
                } else {
                    val resultIntent = Intent()
                    resultIntent.putExtra("selected_category", "$selectedType - $selectedMain - $sub")
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
            binding.llListContainer.addView(itemView)
        }
    }

    private fun showRealEstateOfferType() {
        currentState = CategoryState.OFFER_TYPE
        binding.llListContainer.removeAllViews()
        val offers = listOf("للبيع", "للإيجار")
        for (offer in offers) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_category, binding.llListContainer, false)
            itemView.findViewById<TextView>(R.id.tvItemName).text = offer
            itemView.setOnClickListener {
                val resultIntent = Intent()
                resultIntent.putExtra("selected_category", "$selectedType - $selectedMain - $selectedSub - $offer")
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            binding.llListContainer.addView(itemView)
        }
    }

    override fun onBackPressed() {
        when (currentState) {
            CategoryState.OFFER_TYPE -> showSubCategories(subRealEstate, showIcons = false)
            CategoryState.SUB -> showMainCategories()
            CategoryState.MAIN -> showTypes()
            CategoryState.TYPE -> super.onBackPressed()
        }
    }
}
